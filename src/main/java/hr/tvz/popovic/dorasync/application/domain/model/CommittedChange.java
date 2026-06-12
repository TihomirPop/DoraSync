package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A commit that has not yet been delivered (no row in {@code lead_time_for_changes}), and the
 * earliest deployment shipping it is what lead time for changes measures.
 */
public record CommittedChange(Id commitId, Id repositoryId, Sha sha, Instant committedAt) {

    public CommittedChange {
        requireNonNull(commitId, "commitId must not be null");
        requireNonNull(repositoryId, "repositoryId must not be null");
        requireNonNull(sha, "sha must not be null");
        requireNonNull(committedAt, "committedAt must not be null");
    }
}
