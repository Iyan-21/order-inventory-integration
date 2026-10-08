package edu.cit.Abesia.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.cit.Abesia.inventory.Inventory;
import edu.cit.Abesia.inventory.InventoryService;
import edu.cit.Abesia.shop.OrderResponse;
import edu.cit.Abesia.shop.OrderService;
import edu.cit.Abesia.shop.PlaceOrderRequest;
import edu.cit.Abesia.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns Tiangge feed events into real orders in the Order module and reports the decision.
 * Every step is idempotent: a Tiangge order id maps to exactly one order of ours, and an event id is handled once.
 */
@Component
class ChannelOrderProcessor {

    private enum Plan { FILL, BACKORDER, REJECT }

    private static final Logger log = LoggerFactory.getLogger(ChannelOrderProcessor.class);

    private final TianggeClient client;
    private final ChannelStore store;
    private final OrderService orderService;
    private final InventoryService inventory;
    private final SupplierGateway supplier;
    private final StockSync stockSync;
    private final TransactionTemplate tx;
    private final ChannelLock channelLock;

    ChannelOrderProcessor(TianggeClient client, ChannelStore store, OrderService orderService,
                          InventoryService inventory, SupplierGateway supplier, StockSync stockSync,
                          TransactionTemplate tx, ChannelLock channelLock) {
        this.client = client;
        this.store = store;
        this.orderService = orderService;
        this.inventory = inventory;
        this.supplier = supplier;
        this.stockSync = stockSync;
        this.tx = tx;
        this.channelLock = channelLock;
    }

    /** Handles one feed event. Throws if it could not be completed, so the cursor does not move past it. */
    void handle(JsonNode event) {
        String eventId = event.path("eventId").asText();
        String type = event.path("type").asText();
        channelLock.lock.lock();
        try {
            if (store.eventDone(eventId)) {
                log.info("Event {} already processed; ignoring redelivery", eventId);
                return;
            }
            switch (type) {
                case "ORDER_PLACED" -> onPlaced(event);
                case "ORDER_CANCELLED" -> onCancelled(event);
                default -> log.warn("Ignoring unknown feed event type {}", type);
            }
            store.markEventDone(eventId);
        } finally {
            channelLock.lock.unlock();
        }
    }

    // ---------------------------------------------------------------- ORDER_PLACED

    private void onPlaced(JsonNode event) {
        String tid = event.path("orderId").asText();
        try (StockSync.Scope scope = stockSync.defer()) {
            ChannelStore.ChannelOrderRow row = store.find(tid).orElse(null);
            if (row == null) {
                row = createLocal(tid, event);
            }
            if (!row.decisionSent()) {
                sendDecision(row);
            }
            scope.complete(); // stock updates go out only after Tiangge has our decision
        }
    }

    private ChannelStore.ChannelOrderRow createLocal(String tid, JsonNode event) {
        List<PlaceOrderRequest.LineItem> items = new ArrayList<>();
        Map<String, Integer> need = new LinkedHashMap<>();
        ArrayNode linesJson = TianggeClient.array();
        for (JsonNode l : event.path("lines")) {
            String sku = l.path("sellerSku").asText();
            int qty = l.path("qty").asInt();
            PlaceOrderRequest.LineItem item = new PlaceOrderRequest.LineItem();
            item.setProductId(sku);
            item.setQuantity(qty);
            items.add(item);
            need.merge(sku, qty, Integer::sum);
            ObjectNode j = TianggeClient.object();
            j.put("sellerSku", sku);
            j.put("qty", qty);
            linesJson.add(j);
        }

        Plan plan = plan(need);   // may place supplier orders; done outside the DB transaction

        // Order creation and our bookkeeping commit together: no half-processed order after a crash.
        tx.executeWithoutResult(st -> {
            int shopOrderId;
            String decision;
            String reason = null;
            if (plan == Plan.BACKORDER) {
                shopOrderId = orderService.placeBackorder(items);
                decision = "BACKORDERED";
            } else {
                OrderResponse r = orderService.placeOrder(items);
                shopOrderId = r.getOrderId();
                decision = "CONFIRMED".equals(r.getStatus()) ? "ACCEPTED" : "REJECTED";
                reason = r.getReason();
            }
            store.insertOrder(tid, shopOrderId, decision, reason, linesJson.toString());
        });
        return store.find(tid).orElseThrow();
    }

    private Plan plan(Map<String, Integer> need) {
        Map<String, Integer> shortBy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            Optional<Inventory> inv = inventory.getItem(e.getKey());
            if (inv.isEmpty() || e.getValue() <= 0) {
                return Plan.REJECT; // unknown product or nonsense quantity
            }
            if (e.getValue() > inv.get().getStock()) {
                shortBy.put(e.getKey(), e.getValue() - inv.get().getStock());
            }
        }
        if (shortBy.isEmpty()) {
            return Plan.FILL;
        }
        // Short on stock: BACKORDERED is only allowed when every short item has an open supplier order.
        for (Map.Entry<String, Integer> s : shortBy.entrySet()) {
            if (!supplier.hasOpenOrder(s.getKey())) {
                try {
                    supplier.reorder(s.getKey(), s.getValue());
                } catch (Exception e) {
                    log.warn("Could not place supplier order for {}: {}", s.getKey(), e.getMessage());
                }
            }
        }
        boolean allOpen = shortBy.keySet().stream().allMatch(supplier::hasOpenOrder);
        return allOpen ? Plan.BACKORDER : Plan.REJECT;
    }

    private void sendDecision(ChannelStore.ChannelOrderRow row) {
        try {
            client.decide(row.tianggeOrderId(), row.decision(), row.shopOrderId(), row.reason());
        } catch (TianggeException e) {
            if (!"decision_conflict".equals(e.error())) {
                throw e;
            }
            log.warn("Tiangge already holds a different decision for {}: {}", row.tianggeOrderId(), e.getMessage());
        }
        store.markDecisionSent(row.tianggeOrderId());
        log.info("Decision {} sent for Tiangge order {} (our order {})",
                row.decision(), row.tianggeOrderId(), row.shopOrderId());
    }

    // ------------------------------------------------------------- ORDER_CANCELLED

    private void onCancelled(JsonNode event) {
        String tid = event.path("orderId").asText();
        ChannelStore.ChannelOrderRow row = store.find(tid).orElse(null);
        if (row == null) {
            log.warn("Cancellation for unknown Tiangge order {}; nothing to do", tid);
            return;
        }
        try (StockSync.Scope scope = stockSync.defer()) {
            if (!row.cancelConfirmed()) {
                boolean restocked = "ACCEPTED".equals(row.decision()) || "ACCEPTED".equals(row.resolution());
                if (!row.cancelApplied()) {
                    if (!"REJECTED".equals(row.decision())) {
                        tx.executeWithoutResult(st -> {
                            orderService.cancelOrder(row.shopOrderId()); // restocks if it had been confirmed
                            store.markCancelApplied(tid);
                        });
                    } else {
                        store.markCancelApplied(tid);
                    }
                }
                client.confirmCancellation(tid, restocked);
                store.markCancelConfirmed(tid);
                log.info("Cancellation confirmed for Tiangge order {} (restocked={})", tid, restocked);
            }
            scope.complete(); // new stock goes out after the confirmation
        }
    }
}
