package edu.cit.Abesia.supplier.internal;

import java.util.Map;
import java.util.Optional;

/** Our product id -> LegacySupply SKU and pack size. Single source of truth for the ACL. */
record ProductMapping(String supplierSku, int packSize) {

    private static final Map<String, ProductMapping> BY_PRODUCT = Map.of(
            "P100", new ProductMapping("ZZA-1307", 24),
            "P200", new ProductMapping("ZZA-2133", 12),
            "P300", new ProductMapping("ZZA-4556", 10)
    );

    static Optional<ProductMapping> forProduct(String productId) {
        return Optional.ofNullable(BY_PRODUCT.get(productId));
    }

    /** Whole cases needed to cover the units, rounded up. */
    int casesFor(int unitsNeeded) {
        return (int) Math.ceil((double) unitsNeeded / packSize);
    }
}
