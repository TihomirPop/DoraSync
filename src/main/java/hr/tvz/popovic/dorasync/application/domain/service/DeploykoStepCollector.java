package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.CollectResult;
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

public final class DeploykoStepCollector {

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

    public CollectResult collect(JobStep.CollectDeploykoStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.DEPLOYKO)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    collect(connectionId, DeploykoService.of(externalReference));
            case FetchConnectionPort.Result.NotFound() ->
                    new CollectResult.Failure(new IllegalStateException("No DEPLOYKO connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }

    private CollectResult collect(Id connectionId, DeploykoService service) {
        DeploymentCursor cursor;
        switch (deploykoRepositoryPort.findLatestRecordedAt(connectionId)) {
            case DeploykoRepositoryPort.FindCursorResult.Success(var foundCursor) -> cursor = foundCursor;
            case DeploykoRepositoryPort.FindCursorResult.Failure(var cause) -> {
                return new CollectResult.Failure(cause);
            }
        }

        return switch (fetchDeploykoDeploymentsPort.fetch(service, cursor)) {
            case FetchDeploykoDeploymentsPort.Result.Success(var deployments) ->
                    persist(connectionId, service, terminalOnly(deployments));
            case FetchDeploykoDeploymentsPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }

    private static List<Deployment> terminalOnly(List<Deployment> deployments) {
        return deployments.stream()
                .filter(deployment -> deployment.status().isTerminal())
                .toList();
    }

    private CollectResult persist(Id connectionId, DeploykoService service, List<Deployment> deployments) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            CollectResult result = switch (deploykoRepositoryPort.upsertTarget(connectionId, service)) {
                case DeploykoRepositoryPort.UpsertTargetResult.Success(var deploymentTargetId) ->
                        switch (deploykoRepositoryPort.saveDeployments(deploymentTargetId, deployments)) {
                            case DeploykoRepositoryPort.SaveDeploymentsResult.Success(var savedCount) -> new CollectResult.Success();
                            case DeploykoRepositoryPort.SaveDeploymentsResult.Failure(var cause) -> new CollectResult.Failure(cause);
                        };
                case DeploykoRepositoryPort.UpsertTargetResult.Failure(var cause) -> new CollectResult.Failure(cause);
            };

            if (result instanceof CollectResult.Failure) {
                transaction.rollback();
            }
            return result;
        });

        return switch (transactionResult) {
            case TransactionRunnerPort.Result.Success<CollectResult>(var result) -> result;
            case TransactionRunnerPort.Result.Failure<CollectResult>(var cause) -> new CollectResult.Failure(cause);
        };
    }
}
