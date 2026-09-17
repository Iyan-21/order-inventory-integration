package edu.cit.Abesia.shop;

import edu.cit.Abesia.events.OrderPlacedEvent;
import edu.cit.Abesia.events.OrderRejectedEvent;
import edu.cit.Abesia.inventory.Inventory;
import edu.cit.Abesia.inventory.InventoryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(List<PlaceOrderRequest.LineItem> lineItems) {

        if (lineItems == null || lineItems.isEmpty()) {
            Order rejected = new Order(OrderStatus.REJECTED, "Order must contain at least one item");
            orderRepository.save(rejected);
            eventPublisher.publishEvent(new OrderRejectedEvent(rejected.getOrderId(), rejected.getReason()));
            return new OrderResponse(rejected.getOrderId(), "REJECTED", rejected.getReason(), List.of(), List.of());
        }

        // --- Validation pass: check EVERY line item before reserving ANYTHING ---
        // This is what guarantees all-or-nothing: no InventoryService.reserve() call
        // happens until every single item in the order has been confirmed available.
        for (PlaceOrderRequest.LineItem line : lineItems) {
            Optional<Inventory> itemOpt = inventoryService.getItem(line.getProductId());

            if (itemOpt.isEmpty()) {
                String reason = "Product not found: " + line.getProductId();
                Order rejected = new Order(OrderStatus.REJECTED, reason);
                orderRepository.save(rejected);
                eventPublisher.publishEvent(new OrderRejectedEvent(rejected.getOrderId(), reason));
                return new OrderResponse(rejected.getOrderId(), "REJECTED", reason, List.of(), currentInventorySnapshot());
            }

            if (line.getQuantity() > itemOpt.get().getStock()) {
                String reason = "Insufficient stock for " + line.getProductId();
                Order rejected = new Order(OrderStatus.REJECTED, reason);
                orderRepository.save(rejected);
                eventPublisher.publishEvent(new OrderRejectedEvent(rejected.getOrderId(), reason));
                return new OrderResponse(rejected.getOrderId(), "REJECTED", reason, List.of(), currentInventorySnapshot());
            }
        }

        // --- Reservation pass: everything validated, now actually reserve each item ---
        Order confirmed = new Order(OrderStatus.CONFIRMED, null);
        List<OrderResponse.ItemOutcome> outcomes = new ArrayList<>();

        for (PlaceOrderRequest.LineItem line : lineItems) {
            inventoryService.reserve(line.getProductId(), line.getQuantity());
            confirmed.addItem(new OrderItem(line.getProductId(), line.getQuantity()));
            outcomes.add(new OrderResponse.ItemOutcome(line.getProductId(), "RESERVED"));
        }

        orderRepository.save(confirmed);
        eventPublisher.publishEvent(new OrderPlacedEvent(confirmed.getOrderId()));

        return new OrderResponse(confirmed.getOrderId(), "CONFIRMED", null, outcomes, currentInventorySnapshot());
    }

    @Transactional
    public CancelResult cancelOrder(Integer orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            return CancelResult.notFound();
        }

        Order order = orderOpt.get();

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return CancelResult.alreadyCancelled();
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        return CancelResult.success(order);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    private List<OrderResponse.InventorySnapshot> currentInventorySnapshot() {
        return inventoryService.getAllItems().stream()
                .map(item -> new OrderResponse.InventorySnapshot(
                        item.getProductId(), item.getName(), item.getStock()))
                .toList();
    }

    public static class CancelResult {
        public enum Outcome { SUCCESS, NOT_FOUND, ALREADY_CANCELLED }

        private final Outcome outcome;
        private final Order order;

        private CancelResult(Outcome outcome, Order order) {
            this.outcome = outcome;
            this.order = order;
        }

        public static CancelResult success(Order order) {
            return new CancelResult(Outcome.SUCCESS, order);
        }

        public static CancelResult notFound() {
            return new CancelResult(Outcome.NOT_FOUND, null);
        }

        public static CancelResult alreadyCancelled() {
            return new CancelResult(Outcome.ALREADY_CANCELLED, null);
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public Order getOrder() {
            return order;
        }
    }
}