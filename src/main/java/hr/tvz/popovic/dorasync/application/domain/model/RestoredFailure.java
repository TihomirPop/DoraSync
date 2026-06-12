package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

public record RestoredFailure(
        Id failedDeploymentId,
        Id restoredDeploymentId,
        Id deploymentTargetId,
        Duration restoredAfter,
        boolean incidentStart
) {

    public RestoredFailure {
        requireNonNull(failedDeploymentId, "failedDeploymentId must not be null");
        requireNonNull(restoredDeploymentId, "restoredDeploymentId must not be null");
        requireNonNull(deploymentTargetId, "deploymentTargetId must not be null");
        requireNonNull(restoredAfter, "restoredAfter must not be null");
    }
}
