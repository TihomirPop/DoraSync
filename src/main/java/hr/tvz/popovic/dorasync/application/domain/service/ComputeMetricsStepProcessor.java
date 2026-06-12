package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.CommittedChange;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.DeliveredCommit;
import hr.tvz.popovic.dorasync.application.domain.model.DeployedVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.RestoredFailure;
import hr.tvz.popovic.dorasync.application.domain.model.StepResult;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.LeadTimeRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.TimeToRestoreRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ComputeMetricsStepProcessor implements StepProcessor<JobStep.ComputeMetricsStep> {

    private final TransactionRunnerPort transactionRunner;
    private final FetchConnectionPort fetchConnectionPort;
    private final TimeToRestoreRepositoryPort timeToRestoreRepository;
    private final TimeToRestoreCalculator timeToRestoreCalculator;
    private final LeadTimeRepositoryPort leadTimeRepository;
    private final LeadTimeCalculator leadTimeCalculator;

    public ComputeMetricsStepProcessor(
            TransactionRunnerPort transactionRunner,
            FetchConnectionPort fetchConnectionPort,
            TimeToRestoreRepositoryPort timeToRestoreRepository,
            TimeToRestoreCalculator timeToRestoreCalculator,
            LeadTimeRepositoryPort leadTimeRepository,
            LeadTimeCalculator leadTimeCalculator
    ) {
        this.transactionRunner = transactionRunner;
        this.fetchConnectionPort = fetchConnectionPort;
        this.timeToRestoreRepository = timeToRestoreRepository;
        this.timeToRestoreCalculator = timeToRestoreCalculator;
        this.leadTimeRepository = leadTimeRepository;
        this.leadTimeCalculator = leadTimeCalculator;
    }

    @Override
    public StepResult process(JobStep.ComputeMetricsStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.DEPLOYKO)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    compute(serviceId, connectionId);
            // No Deployko connection means no deployments to derive metrics from: nothing to do.
            case FetchConnectionPort.Result.NotFound() -> new StepResult.Success();
            case FetchConnectionPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult compute(Id serviceId, Id serviceConnectionId) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            StepResult timeToRestore = computeTimeToRestore(serviceConnectionId);
            if (timeToRestore instanceof StepResult.Failure) {
                transaction.rollback();
                return timeToRestore;
            }
            StepResult leadTime = computeLeadTime(serviceId);
            if (leadTime instanceof StepResult.Failure) {
                transaction.rollback();
            }
            return leadTime;
        });

        return switch (transactionResult) {
            case TransactionRunnerPort.Result.Success<StepResult>(var result) -> result;
            case TransactionRunnerPort.Result.Failure<StepResult>(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult computeTimeToRestore(Id serviceConnectionId) {
        return switch (timeToRestoreRepository.loadUnpairedTerminalDeployments(serviceConnectionId)) {
            case TimeToRestoreRepositoryPort.LoadResult.Success(var terminalDeployments) -> {
                List<RestoredFailure> restoredFailures = timeToRestoreCalculator.calculate(terminalDeployments);
                yield switch (timeToRestoreRepository.save(restoredFailures)) {
                    case TimeToRestoreRepositoryPort.SaveResult.Success(var savedCount) -> new StepResult.Success();
                    case TimeToRestoreRepositoryPort.SaveResult.Failure(var cause) -> new StepResult.Failure(cause);
                };
            }
            case TimeToRestoreRepositoryPort.LoadResult.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult computeLeadTime(Id serviceId) {
        return switch (leadTimeRepository.loadUndeliveredCommitsAndNewDeployments(serviceId)) {
            case LeadTimeRepositoryPort.LoadResult.Success(var undeliveredCommits, var newDeployments) ->
                    resolveBuildsAndSave(serviceId, undeliveredCommits, newDeployments);
            case LeadTimeRepositoryPort.LoadResult.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult resolveBuildsAndSave(
            Id serviceId,
            List<CommittedChange> undeliveredCommits,
            List<DeployedVersion> newDeployments
    ) {
        Set<BuildNumber> neededBuildNumbers = new HashSet<>();
        for (DeployedVersion deployment : newDeployments) {
            if (deployment.fallbackBuildNumber() instanceof Maybe.Some<BuildNumber>(var buildNumber)) {
                neededBuildNumbers.add(buildNumber);
            }
        }

        return switch (leadTimeRepository.loadBuilds(serviceId, neededBuildNumbers)) {
            case LeadTimeRepositoryPort.LoadBuildsResult.Success(var builds) -> {
                List<DeliveredCommit> deliveredCommits =
                        leadTimeCalculator.calculate(undeliveredCommits, newDeployments, builds);
                yield switch (leadTimeRepository.save(deliveredCommits)) {
                    case LeadTimeRepositoryPort.SaveResult.Success(var savedCount) -> new StepResult.Success();
                    case LeadTimeRepositoryPort.SaveResult.Failure(var cause) -> new StepResult.Failure(cause);
                };
            }
            case LeadTimeRepositoryPort.LoadBuildsResult.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }
}
