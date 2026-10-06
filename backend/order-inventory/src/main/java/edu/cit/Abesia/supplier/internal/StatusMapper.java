package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.supplier.SupplierOrderStatus;

/** Translates LegacySupply status codes to our own enum. Unknown codes become UNKNOWN. */
final class StatusMapper {
    private StatusMapper() {}

    static SupplierOrderStatus map(int code) {
        return switch (code) {
            case 10 -> SupplierOrderStatus.ACCEPTED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }
}
