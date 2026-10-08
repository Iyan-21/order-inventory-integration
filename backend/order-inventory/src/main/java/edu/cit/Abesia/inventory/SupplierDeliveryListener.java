package edu.cit.Abesia.inventory;

import edu.cit.Abesia.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Restocks when a supplier order is delivered. Depends only on the event, never on the supplier module. */
@Component
class SupplierDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(SupplierDeliveryListener.class);

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Order(Ordered.HIGHEST_PRECEDENCE) // restock first, so anything reacting to the delivery sees the new stock
    @EventListener
    public void onDelivered(SupplierOrderDeliveredEvent event) {
        inventoryService.restock(event.getProductId(), event.getUnitsDelivered());
        log.info("Restocked {} by {} units (delivery {})",
                event.getProductId(), event.getUnitsDelivered(), event.getPoNumber());
    }
}
