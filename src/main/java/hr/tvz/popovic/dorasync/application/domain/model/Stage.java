package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record Stage(
        String name,
        StageStatus status,
        long durationMillis,
        Maybe<Instant> startedAt,
        int order
) {

    public Stage {
        requireNonNull(name, "name must not be null");
        requireNonNull(status, "status must not be null");
        requireNonNull(startedAt, "startedAt must not be null");
    }
}
