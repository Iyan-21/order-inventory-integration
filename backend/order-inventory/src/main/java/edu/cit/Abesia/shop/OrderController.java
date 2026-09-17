package edu.cit.Abesia.shop;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5173")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        OrderResponse response = orderService.placeOrder(request.getItems());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public List<Order> getOrderHistory() {
        return orderService.getAllOrders();
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Integer orderId) {
        OrderService.CancelResult result = orderService.cancelOrder(orderId);

        return switch (result.getOutcome()) {
            case SUCCESS -> ResponseEntity.ok(result.getOrder());
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Order not found");
            case ALREADY_CANCELLED -> ResponseEntity.status(HttpStatus.CONFLICT).body("Order is already cancelled");
        };
    }
}