package edu.cit.Abesia.events;

/** Published by Inventory whenever a product's available stock changes, for any reason. */
public class StockChangedEvent {

    private final String productId;
    private final int available;

    public StockChangedEvent(String productId, int available) {
        this.productId = productId;
        this.available = available;
    }

    public String getProductId() { return productId; }
    public int getAvailable() { return available; }
}
