package edu.cit.Abesia.notification;

import edu.cit.Abesia.events.LowStockEvent;
import edu.cit.Abesia.events.OrderPlacedEvent;
import edu.cit.Abesia.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// This class depends ONLY on the event classes in edu.cit.Abesia.events.
// It never imports InventoryService, OrderService, or anything from the
// shop/inventory packages -- it only reacts to what already happened.
@Component
class NotificationListener {

    private final NotificationRepository notificationRepository;

    NotificationListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        String message = "Order " + event.getOrderId() + " confirmed";
        notificationRepository.save(new Notification(message));
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        String message = "Order " + (event.getOrderId() != null ? event.getOrderId() : "?")
                + " rejected: " + event.getReason();
        notificationRepository.save(new Notification(message));
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        String message = "Reorder needed: " + event.getProductName()
                + " (" + event.getProductId() + ") is low on stock, " + event.getRemainingStock() + " remaining";
        notificationRepository.save(new Notification(message));
    }
}