package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.supplier.SupplierOrder;
import edu.cit.Abesia.supplier.SupplierOrderStatus;
import edu.cit.Abesia.supplier.internal.xml.PurchaseOrderAckXml;
import edu.cit.Abesia.supplier.internal.xml.PurchaseOrderXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Sends PENDING supplier orders later. Always reuses the persisted X-Request-Id, so it can never duplicate a PO. */
@Component
class SupplierOrderRetryJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderRetryJob.class);

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;

    SupplierOrderRetryJob(SupplierOrderRepository repository, LegacySupplyClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    void resendPendingOrders() {
        List<SupplierOrder> pending = repository.findByStatus(SupplierOrderStatus.PENDING);
        if (pending.isEmpty()) return;
        log.info("Retry job found {} PENDING supplier order(s)", pending.size());

        for (SupplierOrder order : pending) {
            if (!resend(order)) {
                break; // supplier still down / throttled: stop this cycle to protect the quota
            }
        }
    }

    private boolean resend(SupplierOrder order) {
        ProductMapping mapping = ProductMapping.forProduct(order.getProductId()).orElse(null);
        if (mapping == null) {
            log.error("No SKU mapping for pending order {} product {}", order.getId(), order.getProductId());
            order.updateStatus(SupplierOrderStatus.FAILED);
            repository.save(order);
            return true;
        }
        try {
            PurchaseOrderAckXml ack = client.placeOrder(
                    new PurchaseOrderXml(mapping.supplierSku(), order.getCases(), order.getBuyerRef()),
                    order.getRequestId());
            order.markSent(ack.PoNumber, StatusMapper.map(ack.StatusCode));
            repository.save(order);
            log.info("Resent pending order {} -> {}", order.getBuyerRef(), order.getStatus());
            return true;
        } catch (Exception e) {
            log.warn("Retry failed for {}: {}", order.getBuyerRef(), e.getMessage());
            return false;
        }
    }
}
