package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record TerminalDeployment(Id id, Id deploymentTargetId, DeploymentStatus status, Instant recordedAt) {

    public TerminalDeployment {
        requireNonNull(id, "id must not be null");
        requireNonNull(deploymentTargetId, "deploymentTargetId must not be null");
        requireNonNull(status, "status must not be null");
        requireNonNull(recordedAt, "recordedAt must not be null");
    }
}
