package edu.cit.Abesia.events;

public class OrderPlacedEvent {

    private final Integer orderId;

    public OrderPlacedEvent(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getOrderId() {
        return orderId;
    }
}