package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * A commit linked to the earliest deployment that shipped it, plus the lead time between the two.
 * One {@code lead_time_for_changes} row.
 */
public record DeliveredCommit(Id commitId, Id deploymentId, Id repositoryId, Duration leadTime) {

    public DeliveredCommit {
        requireNonNull(commitId, "commitId must not be null");
        requireNonNull(deploymentId, "deploymentId must not be null");
        requireNonNull(repositoryId, "repositoryId must not be null");
        requireNonNull(leadTime, "leadTime must not be null");
    }
}
