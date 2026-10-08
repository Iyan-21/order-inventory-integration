package edu.cit.Abesia.channel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.cit.Abesia.events.StockChangedEvent;
import edu.cit.Abesia.inventory.Inventory;
import edu.cit.Abesia.inventory.InventoryService;
import edu.cit.Abesia.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes new stock numbers to Tiangge whenever Inventory announces a change (event driven, no timer).
 * While an order decision is in flight on this thread, updates are held back and sent after the decision.
 * Failed publishes are retried every few seconds until they get through.
 */
@Component
class StockSync {

    interface Scope extends AutoCloseable {
        void complete();

        @Override
        void close();
    }

    private static final Logger log = LoggerFactory.getLogger(StockSync.class);
    private static final ThreadLocal<Set<String>> DEFERRED = new ThreadLocal<>();

    private final TianggeClient client;
    private final InventoryService inventory;
    private final SupplierGateway supplier;
    private final Set<String> retry = ConcurrentHashMap.newKeySet();
    private volatile boolean live;

    StockSync(TianggeClient client, InventoryService inventory, SupplierGateway supplier) {
        this.client = client;
        this.inventory = inventory;
        this.supplier = supplier;
    }

    void setLive(boolean live) { this.live = live; }

    /** Hold stock updates raised on this thread until the scope completes (after the decision is sent). */
    Scope defer() {
        DEFERRED.set(new LinkedHashSet<>());
        return new Scope() {
            private boolean done;

            @Override
            public void complete() { done = true; }

            @Override
            public void close() {
                Set<String> held = DEFERRED.get();
                DEFERRED.remove();
                if (held == null || held.isEmpty()) {
                    return;
                }
                if (done) {
                    publish(held);
                } else {
                    retry.addAll(held);
                }
            }
        };
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onStockChanged(StockChangedEvent event) {
        Set<String> held = DEFERRED.get();
        if (held != null) {
            held.add(event.getProductId());
        } else {
            publish(Set.of(event.getProductId()));
        }
    }

    void publishAll() {
        Set<String> all = new LinkedHashSet<>();
        for (Inventory item : inventory.getAllItems()) {
            all.add(item.getProductId());
        }
        publish(all);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    void flushRetries() {
        if (!live || retry.isEmpty()) {
            return;
        }
        Set<String> batch = new LinkedHashSet<>(retry);
        retry.removeAll(batch);
        publish(batch);
    }

    private void publish(Set<String> productIds) {
        if (!live) {
            return; // go-live publishes everything
        }
        ArrayNode entries = TianggeClient.array();
        for (String id : productIds) {
            if (supplier.supplierSkuFor(id).isEmpty()) {
                continue; // not a listed product
            }
            inventory.getItem(id).ifPresent(item -> {
                ObjectNode e = TianggeClient.object();
                e.put("sellerSku", item.getProductId());
                e.put("available", Math.max(0, item.getStock()));
                entries.add(e);
            });
        }
        if (entries.isEmpty()) {
            return;
        }
        try {
            client.putStock(entries);
            log.info("Published stock to Tiangge: {}", entries);
        } catch (Exception e) {
            log.warn("Stock publish failed ({}); will retry", e.getMessage());
            retry.addAll(productIds);
        }
    }
}
