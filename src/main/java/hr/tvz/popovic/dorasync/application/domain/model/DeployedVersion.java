package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A successful deployment to resolve to the commit it shipped. The commit is identified directly by
 * {@link #commitSha} when present, otherwise by the build number encoded in {@link #imageVersion}
 * (the {@code {buildNumber}-{sha.first7}} fallback).
 */
public record DeployedVersion(Id deploymentId, ImageVersion imageVersion, Maybe<Sha> commitSha, Instant recordedAt) {

    public DeployedVersion {
        requireNonNull(deploymentId, "deploymentId must not be null");
        requireNonNull(imageVersion, "imageVersion must not be null");
        requireNonNull(commitSha, "commitSha must not be null");
        requireNonNull(recordedAt, "recordedAt must not be null");
    }

    /**
     * The build number to look up for the fallback resolution: {@link Maybe.None} when the
     * deployment already carries a SHA (no fallback needed) or the image version has no parseable
     * build number.
     */
    public Maybe<BuildNumber> fallbackBuildNumber() {
        return commitSha instanceof Maybe.Some<Sha> ? new Maybe.None<>() : imageVersion.buildNumber();
    }
}
