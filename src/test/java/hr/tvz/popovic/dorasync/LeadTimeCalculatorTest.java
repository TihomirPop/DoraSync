package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.application.domain.model.BuildIdentity;
import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.CommittedChange;
import hr.tvz.popovic.dorasync.application.domain.model.DeliveredCommit;
import hr.tvz.popovic.dorasync.application.domain.model.DeployedVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.ImageVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.domain.service.LeadTimeCalculator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadTimeCalculatorTest {

    private final LeadTimeCalculator calculator = new LeadTimeCalculator();

    private final Id repository = new Id(UUID.randomUUID());
    private final Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void linksACommitToItsDeploymentByShaWithLeadTimeFromCommitToDeployment() {
        var commit = commit("aaaaaaa1", t0.plusSeconds(100));
        var deployment = deployBySha("aaaaaaa1", t0.plusSeconds(150));

        var delivered = calculator.calculate(List.of(commit), List.of(deployment), List.of());

        assertEquals(1, delivered.size());
        assertEquals(commit.commitId(), delivered.getFirst().commitId());
        assertEquals(deployment.deploymentId(), delivered.getFirst().deploymentId());
        assertEquals(repository, delivered.getFirst().repositoryId());
        assertEquals(Duration.ofSeconds(50), delivered.getFirst().leadTime());
    }

    @Test
    void fallsBackToBuildNumberWhenTheDeploymentHasNoSha() {
        var commit = commit("bbbbbbb2", t0.plusSeconds(100));
        var build = new BuildIdentity(new BuildNumber(42), Maybe.of(new Sha("bbbbbbb2")));
        var deployment = deployByImage("42-bbbbbbb", t0.plusSeconds(150));

        var delivered = calculator.calculate(List.of(commit), List.of(deployment), List.of(build));

        assertEquals(1, delivered.size());
        assertEquals(commit.commitId(), delivered.getFirst().commitId());
        assertEquals(deployment.deploymentId(), delivered.getFirst().deploymentId());
        assertEquals(Duration.ofSeconds(50), delivered.getFirst().leadTime());
    }

    @Test
    void commitWithoutItsOwnDeploymentIsCoveredByALaterDeploymentOfANewerCommit() {
        var older = commit("aaaaaaa1", t0.plusSeconds(100));
        var newer = commit("bbbbbbb2", t0.plusSeconds(200));
        // A single deployment ships only the newer commit, but it also delivers the older one.
        var deployment = deployBySha("bbbbbbb2", t0.plusSeconds(250));

        var delivered = calculator.calculate(List.of(older, newer), List.of(deployment), List.of());

        assertEquals(2, delivered.size());
        assertEquals(deployment.deploymentId(), byCommit(delivered, older).deploymentId());
        assertEquals(Duration.ofSeconds(150), byCommit(delivered, older).leadTime(), "older commit (t+100) to deploy (t+250)");
        assertEquals(deployment.deploymentId(), byCommit(delivered, newer).deploymentId());
        assertEquals(Duration.ofSeconds(50), byCommit(delivered, newer).leadTime());
    }

    @Test
    void linksToTheEarliestCoveringDeployment() {
        var commit = commit("aaaaaaa1", t0.plusSeconds(100));
        var earlier = deployBySha("aaaaaaa1", t0.plusSeconds(150));
        var later = deployBySha("aaaaaaa1", t0.plusSeconds(400));

        var delivered = calculator.calculate(List.of(commit), List.of(later, earlier), List.of());

        assertEquals(1, delivered.size());
        assertEquals(earlier.deploymentId(), delivered.getFirst().deploymentId(), "the earliest deployment wins regardless of input order");
        assertEquals(Duration.ofSeconds(50), delivered.getFirst().leadTime());
    }

    @Test
    void commitNoDeploymentShipsYetProducesNoRow() {
        var delivered = commit("aaaaaaa1", t0.plusSeconds(100));
        var undelivered = commit("ccccccc3", t0.plusSeconds(300));
        // The only deployment ships the older commit; it cannot deliver the newer one.
        var deployment = deployBySha("aaaaaaa1", t0.plusSeconds(150));

        var rows = calculator.calculate(List.of(delivered, undelivered), List.of(deployment), List.of());

        assertEquals(1, rows.size(), "the newer commit is deferred until a deployment covers it");
        assertEquals(delivered.commitId(), rows.getFirst().commitId());
    }

    @Test
    void deploymentWithNoShaAndAnUnknownBuildNumberDeliversNothing() {
        var commit = commit("aaaaaaa1", t0.plusSeconds(100));
        var deployment = deployByImage("999-unknown", t0.plusSeconds(150));

        var rows = calculator.calculate(List.of(commit), List.of(deployment), List.of());

        assertTrue(rows.isEmpty(), "an unresolvable deployment covers nothing");
    }

    private static DeliveredCommit byCommit(List<DeliveredCommit> delivered, CommittedChange commit) {
        return delivered.stream()
                .filter(d -> d.commitId().equals(commit.commitId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no delivered commit for " + commit.commitId()));
    }

    private CommittedChange commit(String sha, Instant committedAt) {
        return new CommittedChange(new Id(UUID.randomUUID()), repository, new Sha(sha), committedAt);
    }

    private static DeployedVersion deployBySha(String sha, Instant recordedAt) {
        return new DeployedVersion(new Id(UUID.randomUUID()), new ImageVersion("image-" + sha), Maybe.of(new Sha(sha)), recordedAt);
    }

    private static DeployedVersion deployByImage(String imageVersion, Instant recordedAt) {
        return new DeployedVersion(new Id(UUID.randomUUID()), new ImageVersion(imageVersion), new Maybe.None<>(), recordedAt);
    }
}
