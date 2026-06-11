package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public sealed interface CommitCursor {

    record Since(Instant committedAt) implements CommitCursor {

        public Since {
            requireNonNull(committedAt, "committedAt must not be null");
        }
    }

    record Beginning() implements CommitCursor {
    }
}
