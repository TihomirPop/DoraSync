package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Duration;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record Service(Id id, ServiceName name, Instant nextSyncAt) {

    private static final Duration SYNC_INTERVAL = Duration.ofHours(1);

    public Service {
        requireNonNull(id, "id must not be null");
        requireNonNull(name, "name must not be null");
        requireNonNull(nextSyncAt, "nextSyncAt must not be null");
    }

    public Service reschedule() {
        return new Service(id, name, Instant.now().plus(SYNC_INTERVAL));
    }
}
