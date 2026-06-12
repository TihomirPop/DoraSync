package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.domain.model.RestoredFailure;
import hr.tvz.popovic.dorasync.application.domain.model.StepResult;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.TimeToRestoreRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.List;

public final class ComputeMetricsStepProcessor implements StepProcessor<JobStep.ComputeMetricsStep> {

    private final TransactionRunnerPort transactionRunner;
    private final FetchConnectionPort fetchConnectionPort;
    private final TimeToRestoreRepositoryPort timeToRestoreRepository;
    private final TimeToRestoreCalculator timeToRestoreCalculator;

    public ComputeMetricsStepProcessor(
            TransactionRunnerPort transactionRunner,
            FetchConnectionPort fetchConnectionPort,
            TimeToRestoreRepositoryPort timeToRestoreRepository,
            TimeToRestoreCalculator timeToRestoreCalculator
    ) {
        this.transactionRunner = transactionRunner;
        this.fetchConnectionPort = fetchConnectionPort;
        this.timeToRestoreRepository = timeToRestoreRepository;
        this.timeToRestoreCalculator = timeToRestoreCalculator;
    }

    @Override
    public StepResult process(JobStep.ComputeMetricsStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.DEPLOYKO)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    computeTimeToRestore(connectionId);
            // No Deployko connection means no deployments to derive time-to-restore from: nothing to do.
            case FetchConnectionPort.Result.NotFound() -> new StepResult.Success();
            case FetchConnectionPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult computeTimeToRestore(Id serviceConnectionId) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            StepResult result = pairAndSave(serviceConnectionId);
            if (result instanceof StepResult.Failure) {
                transaction.rollback();
            }
            return result;
        });

        return switch (transactionResult) {
            case TransactionRunnerPort.Result.Success<StepResult>(var result) -> result;
            case TransactionRunnerPort.Result.Failure<StepResult>(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult pairAndSave(Id serviceConnectionId) {
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
}
