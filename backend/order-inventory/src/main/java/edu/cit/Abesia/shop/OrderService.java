package edu.cit.Abesia.shop;

import edu.cit.Abesia.inventory.Inventory;
import edu.cit.Abesia.inventory.InventoryService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrderService {

    // Depends ONLY on the InventoryService interface -> constructor injection.
    // Spring wires in the actual InventoryServiceImpl bean at runtime, but this
    // class has no compile-time visibility into that implementation class at all
    // (it's package-private in a different package), which is exactly the
    // enforced module boundary the assignment requires.
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    public OrderResponse placeOrder(String productId, int quantity) {
        Optional<Inventory> itemOpt = inventoryService.getItem(productId);

        if (itemOpt.isEmpty()) {
            Order rejected = new Order(productId, quantity, OrderStatus.REJECTED, "Product not found");
            orderRepository.save(rejected);
            return new OrderResponse("REJECTED", "Product not found", null);
        }

        boolean reserved = inventoryService.reserve(productId, quantity);

        if (!reserved) {
            Order rejected = new Order(productId, quantity, OrderStatus.REJECTED, "Insufficient stock");
            orderRepository.save(rejected);

            Inventory current = itemOpt.get();
            OrderResponse.InventorySnapshot snapshot = new OrderResponse.InventorySnapshot(
                    current.getProductId(), current.getName(), current.getStock());

            return new OrderResponse("REJECTED", "Insufficient stock", snapshot);
        }

        Order confirmed = new Order(productId, quantity, OrderStatus.CONFIRMED, null);
        orderRepository.save(confirmed);

        // Re-fetch to get the updated stock level after reservation
        Inventory updated = inventoryService.getItem(productId).orElseThrow();
        OrderResponse.InventorySnapshot snapshot = new OrderResponse.InventorySnapshot(
                updated.getProductId(), updated.getName(), updated.getStock());

        return new OrderResponse("CONFIRMED", null, snapshot);
    }
}