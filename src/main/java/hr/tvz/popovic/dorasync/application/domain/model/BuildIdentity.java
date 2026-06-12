package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

/**
 * The commit a build was built from, keyed by its build number. Used to resolve a deployment to a
 * commit via the {@code {buildNumber}-{sha.first7}} image-version fallback.
 */
public record BuildIdentity(BuildNumber buildNumber, Maybe<Sha> commitSha) {

    public BuildIdentity {
        requireNonNull(buildNumber, "buildNumber must not be null");
        requireNonNull(commitSha, "commitSha must not be null");
    }
}
