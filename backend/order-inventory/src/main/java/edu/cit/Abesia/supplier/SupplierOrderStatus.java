package edu.cit.Abesia.supplier;

public enum SupplierOrderStatus {
    PENDING,    // not yet sent, or sent but no ack yet
    ACCEPTED,   // LS StatusCode 10
    PICKING,    // LS StatusCode 20
    SHIPPED,    // LS StatusCode 30
    DELIVERED,  // LS StatusCode 40
    CANCELLED,  // LS StatusCode 90 (undocumented): order will never arrive
    FAILED,     // gave up after retries
    UNKNOWN     // LS returned a status code we don't recognize
}