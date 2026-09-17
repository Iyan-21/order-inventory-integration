package edu.cit.Abesia.shop;
import edu.cit.Abesia.inventory.Inventory;
import java.util.List;
import java.util.stream.Collectors;
public class OrderResponse {

    private Integer orderId;
    private String status;
    private String reason;
    private List<ItemOutcome> items;
    private List<InventorySnapshot> inventory;

    public OrderResponse(Integer orderId, String status, String reason,
                         List<ItemOutcome> items, List<InventorySnapshot> inventory) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.items = items;
        this.inventory = inventory;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public List<ItemOutcome> getItems() {
        return items;
    }

    public List<InventorySnapshot> getInventory() {
        return inventory;
    }

    public static class ItemOutcome {
        private String productId;
        private String outcome;

        public ItemOutcome(String productId, String outcome) {
            this.productId = productId;
            this.outcome = outcome;
        }

        public String getProductId() {
            return productId;
        }

        public String getOutcome() {
            return outcome;
        }
    }

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