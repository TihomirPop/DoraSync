package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Duration;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record LockedUntil(Instant value) {

    public LockedUntil {
        requireNonNull(value, "value must not be null");
    }

    public static LockedUntil nowPlusThirtyMinutes() {
        return new LockedUntil(Instant.now().plus(Duration.ofMinutes(30)));
    }

}

