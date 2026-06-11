package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public sealed interface DeploymentCursor {

    record Since(Instant recordedAt) implements DeploymentCursor {

        public Since {
            requireNonNull(recordedAt, "recordedAt must not be null");
        }
    }

    record Beginning() implements DeploymentCursor {
    }
}
