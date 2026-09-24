package edu.cit.Abesia.supplier.internal;

import java.time.Instant;

class LegacySupplySession {
    private String token;
    private Instant issuedAt;

    boolean hasToken() {
        return token != null;
    }

    String getToken() {
        return token;
    }

    void set(String token) {
        this.token = token;
        this.issuedAt = Instant.now();
    }

    void invalidate() {
        this.token = null;
        this.issuedAt = null;
    }
}