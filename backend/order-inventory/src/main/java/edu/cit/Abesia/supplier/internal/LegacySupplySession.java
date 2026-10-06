package edu.cit.Abesia.supplier.internal;

import java.time.Duration;
import java.time.Instant;

/** Holds the current LegacySupply token and remembers how old it was when it was last rejected. */
class LegacySupplySession {
    private String token;
    private Instant issuedAt;

    synchronized boolean hasToken() { return token != null; }
    synchronized String getToken() { return token; }

    synchronized void set(String token) {
        this.token = token;
        this.issuedAt = Instant.now();
    }

    /** Drops the token; returns how long it lived (for measuring the real session lifetime in the logs). */
    synchronized Duration invalidate() {
        Duration age = issuedAt == null ? Duration.ZERO : Duration.between(issuedAt, Instant.now());
        this.token = null;
        this.issuedAt = null;
        return age;
    }
}
