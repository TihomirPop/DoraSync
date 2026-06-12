package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.StepResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Deployment;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentCursor;
import hr.tvz.popovic.dorasync.application.domain.model.DeploykoService;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.DeploykoRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchDeploykoDeploymentsPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.List;

public final class DeploykoStepCollector implements StepProcessor<JobStep.CollectDeploykoStep> {

    private final FetchConnectionPort fetchConnectionPort;
    private final FetchDeploykoDeploymentsPort fetchDeploykoDeploymentsPort;
    private final DeploykoRepositoryPort deploykoRepositoryPort;
    private final TransactionRunnerPort transactionRunner;

    public DeploykoStepCollector(
            FetchConnectionPort fetchConnectionPort,
            FetchDeploykoDeploymentsPort fetchDeploykoDeploymentsPort,
            DeploykoRepositoryPort deploykoRepositoryPort,
            TransactionRunnerPort transactionRunner
    ) {
        this.fetchConnectionPort = fetchConnectionPort;
        this.fetchDeploykoDeploymentsPort = fetchDeploykoDeploymentsPort;
        this.deploykoRepositoryPort = deploykoRepositoryPort;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public StepResult process(JobStep.CollectDeploykoStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.DEPLOYKO)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    collect(connectionId, DeploykoService.of(externalReference));
            case FetchConnectionPort.Result.NotFound() ->
                    new StepResult.Failure(new IllegalStateException("No DEPLOYKO connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult collect(Id connectionId, DeploykoService service) {
        DeploymentCursor cursor;
        switch (deploykoRepositoryPort.findLatestRecordedAt(connectionId)) {
            case DeploykoRepositoryPort.FindCursorResult.Success(var foundCursor) -> cursor = foundCursor;
            case DeploykoRepositoryPort.FindCursorResult.Failure(var cause) -> {
                return new StepResult.Failure(cause);
            }
        }

        return switch (fetchDeploykoDeploymentsPort.fetch(service, cursor)) {
            case FetchDeploykoDeploymentsPort.Result.Success(var deployments) ->
                    persist(connectionId, terminalOnly(deployments));
            case FetchDeploykoDeploymentsPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private static List<Deployment> terminalOnly(List<Deployment> deployments) {
        return deployments.stream()
                .filter(deployment -> deployment.status().isTerminal())
                .toList();
    }

    private StepResult persist(Id connectionId, List<Deployment> deployments) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            StepResult result = switch (deploykoRepositoryPort.upsertTarget(connectionId)) {
                case DeploykoRepositoryPort.UpsertTargetResult.Success(var deploymentTargetId) ->
                        switch (deploykoRepositoryPort.saveDeployments(deploymentTargetId, deployments)) {
                            case DeploykoRepositoryPort.SaveDeploymentsResult.Success(var savedCount) -> new StepResult.Success();
                            case DeploykoRepositoryPort.SaveDeploymentsResult.Failure(var cause) -> new StepResult.Failure(cause);
                        };
                case DeploykoRepositoryPort.UpsertTargetResult.Failure(var cause) -> new StepResult.Failure(cause);
            };

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
}
