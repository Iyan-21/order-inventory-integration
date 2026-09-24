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
import java.util.Map;

@Component
class SupplierOrderRetryJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderRetryJob.class);

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;

    // duplicated from SupplierGatewayImpl for now — see note below
    private static final Map<String, String> SKU_BY_PRODUCT = Map.of(
            "P100", "ZZA-1307",
            "P200", "ZZA-2133",
            "P300", "ZZA-4556"
    );

    SupplierOrderRetryJob(SupplierOrderRepository repository, LegacySupplyClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Scheduled(fixedDelay = 60_000) // every 60s; tune to stay within LegacySupply's quota
    void resendPendingOrders() {
        List<SupplierOrder> pending = repository.findByStatus(SupplierOrderStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retry job found {} PENDING supplier order(s)", pending.size());

        for (SupplierOrder order : pending) {
            resend(order);
        }
    }

    private void resend(SupplierOrder order) {
        String sku = SKU_BY_PRODUCT.get(order.getProductId());
        if (sku == null) {
            log.error("No SKU mapping for pending order {} product {}", order.getId(), order.getProductId());
            return;
        }
        try {
            PurchaseOrderXml req = new PurchaseOrderXml(sku, order.getCases(), order.getBuyerRef());
            // Same requestId every time — order.getRequestId() was persisted on first attempt,
            // so this is the same X-Request-Id LegacySupply may have already seen.
            PurchaseOrderAckXml ack = client.placeOrder(req, order.getRequestId());

            SupplierOrderStatus status = mapStatusCode(ack.StatusCode);
            order.markSent(ack.PoNumber, status);
            repository.save(order);
            log.info("Resent pending order {} -> status {}", order.getId(), status);
        } catch (Exception e) {
            // still PENDING, still safe, will be picked up again next run
            log.warn("Retry failed for pending order {}: {}", order.getId(), e.getMessage());
        }
    }

    private SupplierOrderStatus mapStatusCode(int code) {
        return switch (code) {
            case 10 -> SupplierOrderStatus.ACCEPTED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }
}