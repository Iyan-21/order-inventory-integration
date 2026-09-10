package edu.cit.Abesia.shop;

public class PlaceOrderRequest {

    private String productId;
    private int quantity;

    public PlaceOrderRequest() {
        // needed for JSON deserialization
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}