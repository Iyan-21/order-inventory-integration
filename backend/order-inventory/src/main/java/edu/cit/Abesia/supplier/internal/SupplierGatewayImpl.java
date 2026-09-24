package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.supplier.*;
import edu.cit.Abesia.supplier.internal.xml.PurchaseOrderAckXml;
import edu.cit.Abesia.supplier.internal.xml.PurchaseOrderXml;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private final LegacySupplyClient client;
    private final SupplierOrderRepository repository;

    // productId -> [supplierSku, packSize]
    private static final Map<String, ProductMapping> MAPPING = Map.of(
            "P100", new ProductMapping("ZZA-1307", 24),
            "P200", new ProductMapping("ZZA-2133", 12),
            "P300", new ProductMapping("ZZA-4556", 10)
    );

    SupplierGatewayImpl(LegacySupplyClient client, SupplierOrderRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    @Override
    public SupplierOrderResult reorder(String productId, int unitsNeeded) {
        ProductMapping mapping = MAPPING.get(productId);
        if (mapping == null) {
            throw new IllegalArgumentException("No supplier mapping for product " + productId);
        }

        // round UP to whole cases
        int cases = (int) Math.ceil((double) unitsNeeded / mapping.packSize());
        int actualUnits = cases * mapping.packSize();

        // 1. Insert PENDING row with placeholder refs, just to obtain an id
        SupplierOrder order = new SupplierOrder(productId, "PENDING-" + System.nanoTime(), "PENDING-" + System.nanoTime(), cases, actualUnits);
        order = repository.save(order);

        // 2. Derive the real, id-based refs and persist them BEFORE calling out.
        //    This is what makes the row durable and retry-identifiable even if
        //    the call below never returns.
        String buyerRef = "RO-" + order.getId();
        String requestId = "REQ-" + order.getId();
        order.assignRefs(buyerRef, requestId);
        repository.save(order);

        // 3. Only now attempt the external call.
        PurchaseOrderXml req = new PurchaseOrderXml(mapping.supplierSku(), cases, buyerRef);
        PurchaseOrderAckXml ack = client.placeOrder(req, requestId);

        SupplierOrderStatus status = mapStatusCode(ack.StatusCode);
        order.markSent(ack.PoNumber, status);
        repository.save(order);

        return new SupplierOrderResult(order.getId(), buyerRef, status, ack.PoNumber);
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

    private record ProductMapping(String supplierSku, int packSize) {}
}