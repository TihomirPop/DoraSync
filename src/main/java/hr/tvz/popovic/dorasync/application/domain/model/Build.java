package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record Build(
        BuildNumber buildNumber,
        BuildResult result,
        long durationMillis,
        Maybe<Sha> commitSha,
        Instant startedAt,
        List<Stage> stages
) {

    public Build {
        requireNonNull(buildNumber, "buildNumber must not be null");
        requireNonNull(result, "result must not be null");
        requireNonNull(commitSha, "commitSha must not be null");
        requireNonNull(startedAt, "startedAt must not be null");
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
}
