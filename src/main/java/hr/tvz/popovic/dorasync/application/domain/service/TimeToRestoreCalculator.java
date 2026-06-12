package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.RestoredFailure;
import hr.tvz.popovic.dorasync.application.domain.model.TerminalDeployment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TimeToRestoreCalculator {

    private static final Comparator<TerminalDeployment> BY_RECORDED_AT_THEN_ID =
            Comparator.comparing(TerminalDeployment::recordedAt)
                    .thenComparing(deployment -> deployment.id().value());

    public List<RestoredFailure> calculate(List<TerminalDeployment> terminalDeployments) {
        Map<UUID, List<TerminalDeployment>> byTarget = terminalDeployments.stream()
                .collect(Collectors.groupingBy(deployment -> deployment.deploymentTargetId().value()));

        List<RestoredFailure> restored = new ArrayList<>();
        for (List<TerminalDeployment> targetDeployments : byTarget.values()) {
            pairWithinTarget(targetDeployments, restored);
        }
        return List.copyOf(restored);
    }

    private static void pairWithinTarget(List<TerminalDeployment> targetDeployments, List<RestoredFailure> restored) {
        List<PendingFailure> pending = new ArrayList<>();
        boolean previousWasFailure = false;

        for (TerminalDeployment deployment : targetDeployments.stream().sorted(BY_RECORDED_AT_THEN_ID).toList()) {
            switch (deployment.status()) {
                case FAILURE -> {
                    pending.add(new PendingFailure(deployment, !previousWasFailure));
                    previousWasFailure = true;
                }
                case SUCCESS -> {
                    for (PendingFailure failure : pending) {
                        restored.add(failure.restoredBy(deployment));
                    }
                    pending.clear();
                    previousWasFailure = false;
                }
                case IN_PROGRESS, CANCELED, UNKNOWN -> {
                    // Not loaded; neither a break nor a restoration. Defensive no-op.
                }
            }
        }
    }

    private record PendingFailure(TerminalDeployment failure, boolean incidentStart) {

        RestoredFailure restoredBy(TerminalDeployment restoration) {
            return new RestoredFailure(
                    failure.id(),
                    restoration.id(),
                    failure.deploymentTargetId(),
                    Duration.between(failure.recordedAt(), restoration.recordedAt()),
                    incidentStart
            );
        }
    }
}
