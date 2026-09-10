package edu.cit.Abesia.shop;

public class OrderResponse {

    private String status;
    private String reason;
    private InventorySnapshot inventory;

    public OrderResponse(String status, String reason, InventorySnapshot inventory) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public InventorySnapshot getInventory() {
        return inventory;
    }

    // Small nested DTO so we never leak the actual Inventory JPA entity
    // (or the inventory package's internals) across the module boundary
    // into the REST response.
    public static class InventorySnapshot {
        private String productId;
        private String name;
        private int stock;

        public InventorySnapshot(String productId, String name, int stock) {
            this.productId = productId;
            this.name = name;
            this.stock = stock;
        }

        public String getProductId() {
            return productId;
        }

        public String getName() {
            return name;
        }

        public int getStock() {
            return stock;
        }
    }
}