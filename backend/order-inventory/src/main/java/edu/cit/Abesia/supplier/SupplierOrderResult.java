package edu.cit.Abesia.supplier;

public class SupplierOrderResult {
    private final Long supplierOrderId;
    private final String buyerRef;
    private final SupplierOrderStatus status;
    private final String poNumber; // null if still pending

    public SupplierOrderResult(Long supplierOrderId, String buyerRef, SupplierOrderStatus status, String poNumber) {
        this.supplierOrderId = supplierOrderId;
        this.buyerRef = buyerRef;
        this.status = status;
        this.poNumber = poNumber;
    }

    public Long getSupplierOrderId() { return supplierOrderId; }
    public String getBuyerRef() { return buyerRef; }
    public SupplierOrderStatus getStatus() { return status; }
    public String getPoNumber() { return poNumber; }
}