package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.supplier.SupplierGateway;
import edu.cit.Abesia.supplier.SupplierOrder;
import edu.cit.Abesia.supplier.SupplierOrderResult;
import edu.cit.Abesia.supplier.SupplierOrderStatus;
import edu.cit.Abesia.supplier.internal.xml.PurchaseOrderAckXml;
import edu.cit.Abesia.supplier.internal.xml.PurchaseOrderXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private final LegacySupplyClient client;
    private final SupplierOrderRepository repository;
    private final TransactionTemplate tx;

    SupplierGatewayImpl(LegacySupplyClient client, SupplierOrderRepository repository, PlatformTransactionManager tm) {
        this.client = client;
        this.repository = repository;
        // REQUIRES_NEW: we are often called from an AFTER_COMMIT listener, where a plain REQUIRED
        // transaction would join the already-committed one and silently never commit our writes.
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public SupplierOrderResult reorder(String productId, int unitsNeeded) {
        ProductMapping mapping = ProductMapping.forProduct(productId)
                .orElseThrow(() -> new IllegalArgumentException("No supplier mapping for product " + productId));
        if (unitsNeeded <= 0) {
            throw new IllegalArgumentException("unitsNeeded must be positive");
        }

        int cases = mapping.casesFor(unitsNeeded);
        int actualUnits = cases * mapping.packSize();

        // 1+2. Insert the row and derive the id-based refs in ONE short transaction,
        //      so a crash can never leave a row with placeholder refs. Durable BEFORE any network call.
        SupplierOrder order = tx.execute(status -> {
            String placeholder = "TMP-" + java.util.UUID.randomUUID();
            SupplierOrder saved = repository.saveAndFlush(
                    new SupplierOrder(productId, placeholder, placeholder, cases, actualUnits));
            saved.assignRefs("RO-" + saved.getId(), "REQ-" + saved.getId());
            return repository.save(saved);
        });

        // 3. Call out. Any failure leaves the row PENDING; the retry job re-sends with the same X-Request-Id.
        try {
            PurchaseOrderAckXml ack = client.placeOrder(
                    new PurchaseOrderXml(mapping.supplierSku(), cases, order.getBuyerRef()), order.getRequestId());
            tx.executeWithoutResult(st -> {
                order.markSent(ack.PoNumber, StatusMapper.map(ack.StatusCode));
                repository.save(order);
            });
        } catch (Exception e) {
            log.warn("LegacySupply unavailable for {} ({}); kept PENDING for the retry job", order.getBuyerRef(), e.getMessage());
        }
        return new SupplierOrderResult(order.getId(), order.getBuyerRef(), order.getStatus(), order.getPoNumber());
    }
}