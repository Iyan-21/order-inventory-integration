package edu.cit.Abesia.supplier;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "supplier_orders")
public class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, unique = true)
    private String buyerRef;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Column(nullable = false)
    private int cases;

    @Column(nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupplierOrder() {}

    public SupplierOrder(String productId, String buyerRef, String requestId, int cases, int units) {
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // getters
    public Long getId() { return id; }
    public String getProductId() { return productId; }
    public String getBuyerRef() { return buyerRef; }
    public String getRequestId() { return requestId; }
    public String getPoNumber() { return poNumber; }
    public int getCases() { return cases; }
    public int getUnits() { return units; }
    public SupplierOrderStatus getStatus() { return status; }

    public void markSent(String poNumber, SupplierOrderStatus status) {
        this.poNumber = poNumber;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(SupplierOrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void assignRefs(String buyerRef, String requestId) {
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.updatedAt = Instant.now();
    }
}