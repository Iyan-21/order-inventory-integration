package edu.cit.Abesia;

import java.time.Instant;
import java.util.UUID;

/** One fresh instance id per application start; sent as X-Client-Instance on every external call. */
public final class AppInstance {
    public static final String ID = UUID.randomUUID().toString();
    public static final Instant STARTED_AT = Instant.now();

    private AppInstance() {}
}
