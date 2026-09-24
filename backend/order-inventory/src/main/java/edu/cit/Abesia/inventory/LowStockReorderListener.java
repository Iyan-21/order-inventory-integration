package edu.cit.Abesia.inventory;

import edu.cit.Abesia.events.LowStockEvent;
import edu.cit.Abesia.supplier.SupplierGateway;
import edu.cit.Abesia.supplier.SupplierOrderResult;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
class LowStockReorderListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockReorderListener.class);
    private static final int RESTOCK_TARGET = 20;

    private final SupplierGateway supplierGateway;

    LowStockReorderListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        int unitsNeeded = RESTOCK_TARGET - event.getRemainingStock();
        if (unitsNeeded <= 0) {
            return;
        }
        try {
            SupplierOrderResult result = supplierGateway.reorder(event.getProductId(), unitsNeeded);
            log.info("Reorder placed for {}: {}", event.getProductId(), result.getStatus());
        } catch (Exception e) {
            // Supplier failure must NEVER affect the order/inventory transaction that already committed.
            log.error("Reorder failed for {}: {}", event.getProductId(), e.getMessage());
        }
    }
}