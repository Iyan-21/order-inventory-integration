package edu.cit.Abesia.events;

/** A supplier order that will never arrive. Units are NOT added to stock. */
public class SupplierOrderCancelledEvent {

    private final String productId;
    private final int unitsNotArriving;
    private final String poNumber;

    public SupplierOrderCancelledEvent(String productId, int unitsNotArriving, String poNumber) {
        this.productId = productId;
        this.unitsNotArriving = unitsNotArriving;
        this.poNumber = poNumber;
    }

    public String getProductId() { return productId; }
    public int getUnitsNotArriving() { return unitsNotArriving; }
    public String getPoNumber() { return poNumber; }
}
