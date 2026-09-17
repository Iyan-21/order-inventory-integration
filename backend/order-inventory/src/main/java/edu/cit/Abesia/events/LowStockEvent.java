package edu.cit.Abesia.events;

public class LowStockEvent {

    private final String productId;
    private final String productName;
    private final int remainingStock;

    public LowStockEvent(String productId, String productName, int remainingStock) {
        this.productId = productId;
        this.productName = productName;
        this.remainingStock = remainingStock;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getRemainingStock() {
        return remainingStock;
    }
}