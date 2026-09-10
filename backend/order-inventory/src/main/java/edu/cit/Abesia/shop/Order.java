package edu.cit.Abesia.shop;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Order() {
        // JPA requires a no-arg constructor
    }

    public Order(String productId, int quantity, OrderStatus status, String reason) {
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public Integer getOrderId() {
        return orderId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}