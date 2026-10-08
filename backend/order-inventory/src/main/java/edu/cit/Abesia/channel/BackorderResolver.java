package edu.cit.Abesia.channel;

import com.fasterxml.jackson.databind.JsonNode;
import edu.cit.Abesia.events.SupplierOrderDeliveredEvent;
import edu.cit.Abesia.shop.OrderService;
import edu.cit.Abesia.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * When a supplier delivery lands, fills the orders that were waiting for it (ACCEPTED), or gives up on those that
 * still cannot be filled and have nothing coming (CANCELLED), and tells Tiangge. Unsent answers are retried.
 */
@Component
class BackorderResolver {

    private static final Logger log = LoggerFactory.getLogger(BackorderResolver.class);

    private final TianggeClient client;
    private final ChannelStore store;
    private final OrderService orderService;
    private final SupplierGateway supplier;
    private final StockSync stockSync;
    private final ChannelLifecycle lifecycle;
    private final TransactionTemplate tx;
    private final ChannelLock channelLock;

    BackorderResolver(TianggeClient client, ChannelStore store, OrderService orderService, SupplierGateway supplier,
                      StockSync stockSync, ChannelLifecycle lifecycle, TransactionTemplate tx, ChannelLock channelLock) {
        this.client = client;
        this.store = store;
        this.orderService = orderService;
        this.supplier = supplier;
        this.stockSync = stockSync;
        this.lifecycle = lifecycle;
        this.tx = tx;
        this.channelLock = channelLock;
    }

    /**
     * Runs after the delivery (and its restock) has been committed. In AFTER_COMMIT a plain transaction would join the
     * finished one and never commit, so the work hops to a thread that has no transaction attached.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onDelivered(SupplierOrderDeliveredEvent event) {
        log.info("Supplier delivery {} landed; resolving waiting backorders", event.getPoNumber());
        CompletableFuture.runAsync(this::run);
    }

    /** Safety net: retries unsent answers and notices backorders whose supplier order disappeared. */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    void tick() {
        run();
    }

    private void run() {
        if (!lifecycle.isLive()) {
            return;
        }
        channelLock.lock.lock();
        try (StockSync.Scope scope = stockSync.defer()) {
            resolvePending();
            sendResolutions();
            scope.complete();
        } catch (Exception e) {
            log.warn("Backorder resolution incomplete, will retry: {}", e.getMessage());
        } finally {
            channelLock.lock.unlock();
        }
    }

    private void resolvePending() {
        for (ChannelStore.ChannelOrderRow row : store.pendingBackorders()) {
            OrderService.BackorderOutcome outcome = tx.execute(st -> {
                OrderService.BackorderOutcome o = orderService.fulfilBackorder(row.shopOrderId());
                if (o == OrderService.BackorderOutcome.CONFIRMED || o == OrderService.BackorderOutcome.ALREADY_CONFIRMED) {
                    store.setResolution(row.tianggeOrderId(), "ACCEPTED");
                }
                return o;
            });
            if (outcome == OrderService.BackorderOutcome.STILL_SHORT) {
                if (stillWaiting(row)) {
                    continue; // another supplier order is still on its way for the missing product
                }
                tx.executeWithoutResult(st -> {
                    orderService.cancelOrder(row.shopOrderId());
                    store.setResolution(row.tianggeOrderId(), "CANCELLED");
                });
                log.info("Backorder {} cannot be filled and nothing is coming: CANCELLED", row.tianggeOrderId());
            } else if (outcome == OrderService.BackorderOutcome.CANCELLED || outcome == OrderService.BackorderOutcome.NOT_FOUND) {
                store.setResolution(row.tianggeOrderId(), "CANCELLED");
            }
        }
    }

    private boolean stillWaiting(ChannelStore.ChannelOrderRow row) {
        for (JsonNode line : TianggeClient.parse(row.linesJson())) {
            if (supplier.hasOpenOrder(line.path("sellerSku").asText())) {
                return true;
            }
        }
        return false;
    }

    private void sendResolutions() {
        for (ChannelStore.ChannelOrderRow row : store.unsentResolutions()) {
            try {
                client.resolve(row.tianggeOrderId(), row.resolution());
            } catch (TianggeException e) {
                if (!"not_backordered".equals(e.error())) {
                    throw e;
                }
                log.warn("Tiangge says {} is no longer backordered: {}", row.tianggeOrderId(), e.getMessage());
            }
            store.markResolutionSent(row.tianggeOrderId());
            log.info("Backorder {} resolved as {}", row.tianggeOrderId(), row.resolution());
        }
    }
}
