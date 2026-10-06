package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.events.SupplierOrderDeliveredEvent;
import edu.cit.Abesia.supplier.SupplierOrder;
import edu.cit.Abesia.supplier.SupplierOrderStatus;
import edu.cit.Abesia.supplier.internal.xml.PurchaseOrderStatusXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Polls open purchase orders and maps LegacySupply codes to our enum.
 * On DELIVERED it publishes SupplierOrderDeliveredEvent; Inventory restocks from the event.
 */
@Component
class SupplierOrderTrackingJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderTrackingJob.class);
    /** Hard cap per cycle so polling can never blow through LegacySupply's quota. */
    private static final int MAX_POLLS_PER_CYCLE = 5;

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    SupplierOrderTrackingJob(SupplierOrderRepository repository, LegacySupplyClient client,
                             ApplicationEventPublisher events, TransactionTemplate tx) {
        this.repository = repository;
        this.client = client;
        this.events = events;
        this.tx = tx;
    }

    @Scheduled(fixedDelay = 90_000, initialDelay = 30_000)
    void pollOpenOrders() {
        // UNKNOWN orders are re-polled too: the code may be a transient oddity.
        List<SupplierOrder> open = repository.findByStatusIn(List.of(
                SupplierOrderStatus.ACCEPTED, SupplierOrderStatus.PICKING,
                SupplierOrderStatus.SHIPPED, SupplierOrderStatus.UNKNOWN));
        int polled = 0;
        for (SupplierOrder order : open) {
            if (order.getPoNumber() == null) continue;
            if (polled++ >= MAX_POLLS_PER_CYCLE) break; // oldest-updated first would be nicer; cap keeps us under quota
            try {
                PurchaseOrderStatusXml status = client.getOrderStatus(order.getPoNumber());
                apply(order, status.StatusCode);
            } catch (Exception e) {
                log.warn("Status check failed for {}: {}", order.getPoNumber(), e.getMessage());
                break; // throttled or down: stop this cycle, try again next time
            }
        }
    }

    private void apply(SupplierOrder order, int code) {
        SupplierOrderStatus next = StatusMapper.map(code);
        if (next == SupplierOrderStatus.UNKNOWN) {
            log.warn("Unexpected LegacySupply status code {} on {}; marked UNKNOWN, no restock, will re-check",
                    code, order.getPoNumber());
        }
        if (next == order.getStatus()) return;

        // Status change + event in one transaction; DELIVERED can only be reached once, so restock happens once.
        tx.executeWithoutResult(s -> {
            order.updateStatus(next);
            repository.save(order);
            if (next == SupplierOrderStatus.DELIVERED) {
                events.publishEvent(new SupplierOrderDeliveredEvent(
                        order.getProductId(), order.getUnits(), order.getPoNumber()));
            }
        });
    }
}
