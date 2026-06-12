package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.BuildIdentity;
import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.CommittedChange;
import hr.tvz.popovic.dorasync.application.domain.model.DeliveredCommit;
import hr.tvz.popovic.dorasync.application.domain.model.DeployedVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LeadTimeCalculator {

    private static final Comparator<CoverPoint> BY_RECORDED_AT_THEN_ID =
            Comparator.comparing(CoverPoint::recordedAt)
                    .thenComparing(point -> point.deploymentId().value());

    /**
     * Links each undelivered commit to the earliest deployment that shipped it or any newer commit.
     * {@code newDeployments} is the bounded tail past the frontier, so the earliest covering
     * deployment for any undelivered commit is guaranteed to be among them. Commits no deployment
     * covers yet produce no row (deferred to a later run).
     */
    public List<DeliveredCommit> calculate(
            List<CommittedChange> undeliveredCommits,
            List<DeployedVersion> newDeployments,
            List<BuildIdentity> builds
    ) {
        Map<Long, String> shaByBuildNumber = shaByBuildNumber(builds);
        Map<String, Instant> committedAtBySha = committedAtBySha(undeliveredCommits);
        List<CoverPoint> coverPoints = coverPoints(newDeployments, shaByBuildNumber, committedAtBySha);

        List<DeliveredCommit> delivered = new ArrayList<>();
        for (CommittedChange commit : undeliveredCommits) {
            // coverPoints are ordered by recordedAt, so the first match is the earliest delivery.
            for (CoverPoint point : coverPoints) {
                if (!point.coveredUpTo().isBefore(commit.committedAt())) {
                    delivered.add(deliver(commit, point));
                    break;
                }
            }
        }
        return List.copyOf(delivered);
    }

    private static List<CoverPoint> coverPoints(
            List<DeployedVersion> newDeployments,
            Map<Long, String> shaByBuildNumber,
            Map<String, Instant> committedAtBySha
    ) {
        List<CoverPoint> coverPoints = new ArrayList<>();
        for (DeployedVersion deployment : newDeployments) {
            String deployedSha = deployedSha(deployment, shaByBuildNumber);
            // A deployment whose commit isn't among the undelivered commits covers nothing new here.
            Instant coveredUpTo = deployedSha == null ? null : committedAtBySha.get(deployedSha);
            if (coveredUpTo != null) {
                coverPoints.add(new CoverPoint(deployment.deploymentId(), deployment.recordedAt(), coveredUpTo));
            }
        }
        coverPoints.sort(BY_RECORDED_AT_THEN_ID);
        return coverPoints;
    }

    private static DeliveredCommit deliver(CommittedChange commit, CoverPoint point) {
        Duration leadTime = Duration.between(commit.committedAt(), point.recordedAt());
        return new DeliveredCommit(
                commit.commitId(),
                point.deploymentId(),
                commit.repositoryId(),
                leadTime.isNegative() ? Duration.ZERO : leadTime
        );
    }

    private static Map<Long, String> shaByBuildNumber(List<BuildIdentity> builds) {
        Map<Long, String> shaByBuildNumber = new HashMap<>();
        for (BuildIdentity build : builds) {
            if (build.commitSha() instanceof Maybe.Some<Sha>(var sha)) {
                shaByBuildNumber.putIfAbsent(build.buildNumber().value(), sha.value());
            }
        }
        return shaByBuildNumber;
    }

    private static Map<String, Instant> committedAtBySha(List<CommittedChange> commits) {
        Map<String, Instant> committedAtBySha = new HashMap<>();
        for (CommittedChange commit : commits) {
            committedAtBySha.merge(commit.sha().value(), commit.committedAt(),
                    (existing, candidate) -> candidate.isBefore(existing) ? candidate : existing);
        }
        return committedAtBySha;
    }

    /** The SHA a deployment shipped: its own SHA when present, else the SHA of its fallback build. */
    private static String deployedSha(DeployedVersion deployment, Map<Long, String> shaByBuildNumber) {
        return switch (deployment.commitSha()) {
            case Maybe.Some<Sha>(var sha) -> sha.value();
            case Maybe.None<Sha>() -> switch (deployment.fallbackBuildNumber()) {
                case Maybe.Some<BuildNumber>(var buildNumber) -> shaByBuildNumber.get(buildNumber.value());
                case Maybe.None<BuildNumber>() -> null;
            };
        };
    }

    private record CoverPoint(Id deploymentId, Instant recordedAt, Instant coveredUpTo) {
    }
}
