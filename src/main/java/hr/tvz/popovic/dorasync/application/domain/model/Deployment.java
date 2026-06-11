package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record Deployment(
        DeploymentId deploymentId,
        ImageVersion imageVersion,
        Maybe<Sha> commitSha,
        DeploymentStatus status,
        Instant recordedAt
) {

    public Deployment {
        requireNonNull(deploymentId, "deploymentId must not be null");
        requireNonNull(imageVersion, "imageVersion must not be null");
        requireNonNull(commitSha, "commitSha must not be null");
        requireNonNull(status, "status must not be null");
        requireNonNull(recordedAt, "recordedAt must not be null");
    }
}
