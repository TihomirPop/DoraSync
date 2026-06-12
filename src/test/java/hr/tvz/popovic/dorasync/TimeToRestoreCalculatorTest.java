package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.application.domain.model.DeploymentStatus;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.RestoredFailure;
import hr.tvz.popovic.dorasync.application.domain.model.TerminalDeployment;
import hr.tvz.popovic.dorasync.application.domain.service.TimeToRestoreCalculator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeToRestoreCalculatorTest {

    private final TimeToRestoreCalculator calculator = new TimeToRestoreCalculator();

    private final Id target = new Id(UUID.randomUUID());
    private final Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void everyFailureOfAStreakIsRecordedButOnlyTheFirstIsTheIncidentStart() {
        var success = deployment(target, DeploymentStatus.SUCCESS, t0);
        var firstFailure = deployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100));
        var secondFailure = deployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(200));
        var recovery = deployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(500));

        var restored = calculator.calculate(List.of(success, firstFailure, secondFailure, recovery));

        assertEquals(2, restored.size(), "both failures of the streak are recorded");

        var first = byFailure(restored, firstFailure);
        assertTrue(first.incidentStart(), "first failure after a success starts the outage");
        assertEquals(recovery.id(), first.restoredDeploymentId());
        assertEquals(Duration.ofSeconds(400), first.restoredAfter(), "first failure (t+100) to recovery (t+500)");

        var second = byFailure(restored, secondFailure);
        assertFalse(second.incidentStart(), "a failure following another failure is not an incident start");
        assertEquals(recovery.id(), second.restoredDeploymentId(), "shares the success that ends the outage");
        assertEquals(Duration.ofSeconds(300), second.restoredAfter());
    }

    @Test
    void trailingFailureWithoutRecoveryIsDeferred() {
        var restored = calculator.calculate(List.of(
                deployment(target, DeploymentStatus.SUCCESS, t0),
                deployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100))
        ));

        assertTrue(restored.isEmpty(), "unresolved failure produces no row yet");
    }

    @Test
    void independentOutagesEachStartANewIncident() {
        var firstFailure = deployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100));
        var firstRecovery = deployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(200));
        var secondFailure = deployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(300));
        var secondRecovery = deployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(400));

        var restored = calculator.calculate(List.of(
                deployment(target, DeploymentStatus.SUCCESS, t0),
                firstFailure, firstRecovery, secondFailure, secondRecovery
        ));

        assertEquals(2, restored.size());
        assertTrue(byFailure(restored, firstFailure).incidentStart());
        assertTrue(byFailure(restored, secondFailure).incidentStart(), "a failure after a recovery starts a new outage");
    }

    @Test
    void failuresAndRecoveriesArePairedPerTargetOnly() {
        var otherTarget = new Id(UUID.randomUUID());

        var failureA = deployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100));
        var recoveryA = deployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(400));
        var failureB = deployment(otherTarget, DeploymentStatus.FAILURE, t0.plusSeconds(200));
        var recoveryB = deployment(otherTarget, DeploymentStatus.SUCCESS, t0.plusSeconds(250));

        var restored = calculator.calculate(List.of(failureA, failureB, recoveryB, recoveryA));

        assertEquals(2, restored.size());
        assertEquals(recoveryA.id(), byFailure(restored, failureA).restoredDeploymentId(), "target A pairs within A");
        assertEquals(recoveryB.id(), byFailure(restored, failureB).restoredDeploymentId(), "target B pairs within B");
    }

    @Test
    void unsortedInputIsPairedCorrectly() {
        var earlierSuccess = deployment(target, DeploymentStatus.SUCCESS, t0);
        var failure = deployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100));
        var recovery = deployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(300));

        // deliberately out of chronological order
        var restored = calculator.calculate(List.of(recovery, failure, earlierSuccess));

        assertEquals(1, restored.size());
        assertTrue(restored.getFirst().incidentStart());
        assertEquals(recovery.id(), restored.getFirst().restoredDeploymentId());
        assertEquals(Duration.ofSeconds(200), restored.getFirst().restoredAfter());
    }

    @Test
    void firstFailureEverWithNoPrecedingSuccessIsAnIncidentStart() {
        var failure = deployment(target, DeploymentStatus.FAILURE, t0);
        var recovery = deployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(120));

        var restored = calculator.calculate(List.of(failure, recovery));

        assertEquals(1, restored.size());
        assertTrue(restored.getFirst().incidentStart(), "first failure ever still starts an outage");
        assertEquals(failure.id(), restored.getFirst().failedDeploymentId());
    }

    private static RestoredFailure byFailure(List<RestoredFailure> restored, TerminalDeployment failure) {
        return restored.stream()
                .filter(r -> r.failedDeploymentId().equals(failure.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no restored failure for " + failure.id()));
    }

    private static TerminalDeployment deployment(Id targetId, DeploymentStatus status, Instant recordedAt) {
        return new TerminalDeployment(new Id(UUID.randomUUID()), targetId, status, recordedAt);
    }
}
