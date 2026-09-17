package edu.cit.Abesia.events;

public class OrderRejectedEvent {

    private final Integer orderId;
    private final String reason;

    public OrderRejectedEvent(Integer orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }
}