package edu.cit.Abesia.supplier;

import java.util.Optional;

public interface SupplierGateway {
    SupplierOrderResult reorder(String productId, int unitsNeeded);

    /** True when a purchase order for this product has been accepted by the supplier and has not been delivered or cancelled. */
    boolean hasOpenOrder(String productId);

    /** The supplier's item number for a product, if the supplier sells it. */
    Optional<String> supplierSkuFor(String productId);
}
