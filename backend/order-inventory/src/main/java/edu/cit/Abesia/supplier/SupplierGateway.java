package edu.cit.Abesia.supplier;

public interface SupplierGateway {
    SupplierOrderResult reorder(String productId, int unitsNeeded);
}