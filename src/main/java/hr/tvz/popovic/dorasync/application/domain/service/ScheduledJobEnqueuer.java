package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.LockedUntil;
import hr.tvz.popovic.dorasync.application.domain.model.Service;
import hr.tvz.popovic.dorasync.application.port.in.EnqueueScheduledJobsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.AddJobStepPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchServiceConnectionsPort;
import hr.tvz.popovic.dorasync.application.port.out.RescheduleServicePort;
import hr.tvz.popovic.dorasync.application.port.out.RunJobPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ScheduledJobEnqueuer implements EnqueueScheduledJobsUseCase {

    private final TransactionRunnerPort transactionRunner;
    private final FetchScheduledServicesPort fetchScheduledServicesPort;
    private final RescheduleServicePort rescheduleServicePort;
    private final FetchServiceConnectionsPort fetchServiceConnectionsPort;
    private final RunJobPort runJobPort;
    private final AddJobStepPort addJobStepPort;

    public ScheduledJobEnqueuer(
            TransactionRunnerPort transactionRunner,
            FetchScheduledServicesPort fetchScheduledServicesPort,
            RescheduleServicePort rescheduleServicePort,
            FetchServiceConnectionsPort fetchServiceConnectionsPort,
            RunJobPort runJobPort,
            AddJobStepPort addJobStepPort
    ) {
        this.transactionRunner = transactionRunner;
        this.fetchScheduledServicesPort = fetchScheduledServicesPort;
        this.rescheduleServicePort = rescheduleServicePort;
        this.fetchServiceConnectionsPort = fetchServiceConnectionsPort;
        this.runJobPort = runJobPort;
        this.addJobStepPort = addJobStepPort;
    }

    @Override
    public Result enqueue() {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            var fetchResult = fetchScheduledServicesPort.fetchScheduledServices();
            return switch (fetchResult) {
                case FetchScheduledServicesPort.Result.Success(var services) -> processServices(services, transaction);
                case FetchScheduledServicesPort.Result.Failure(var cause) -> {
                    transaction.rollback();
                    yield new Result.Failure("Failed to fetch scheduled services", cause);
                }
            };
        });

        return switch (transactionResult) {
            case TransactionRunnerPort.Result.Success<Result>(var result) -> result;
            case TransactionRunnerPort.Result.Failure<Result>(var cause) -> new Result.Failure("Failed to enqueue scheduled jobs", cause);
        };
    }

    private Result processServices(List<Service> services, TransactionRunnerPort.Transaction transaction) {
        List<Id> skippedServiceIds = new ArrayList<>();
        for (Service service : services) {
            switch (processService(service, transaction)) {
                case ProcessResult.Done() -> {
                }
                case ProcessResult.Skipped() -> skippedServiceIds.add(service.id());
                case ProcessResult.Failed(var message, var cause) -> {
                    return new Result.Failure(message, cause);
                }
            }
        }
        return new Result.Success(skippedServiceIds);
    }

    private ProcessResult processService(Service service, TransactionRunnerPort.Transaction transaction) {
        Service rescheduled = service.reschedule();

        var rescheduleResult = rescheduleServicePort.reschedule(service.id(), rescheduled.nextSyncAt());
        switch (rescheduleResult) {
            case RescheduleServicePort.Result.Success() -> {
            }
            case RescheduleServicePort.Result.Failure(var cause) -> {
                transaction.rollback();
                return new ProcessResult.Failed("Failed to reschedule service " + service.id(), cause);
            }
        }

        var connectionsResult = fetchServiceConnectionsPort.fetchConnections(service.id());
        Set<ConnectionType> connectionTypes;
        switch (connectionsResult) {
            case FetchServiceConnectionsPort.Result.Success(var types) -> connectionTypes = types;
            case FetchServiceConnectionsPort.Result.Failure(var cause) -> {
                transaction.rollback();
                return new ProcessResult.Failed("Failed to fetch connections for service " + service.id(), cause);
            }
        }

        if (connectionTypes.isEmpty()) {
            return new ProcessResult.Done();
        }

        var runResult = runJobPort.run(service.id(), LockedUntil.nowPlusTenMinutes());
        Id jobId;
        switch (runResult) {
            case RunJobPort.Result.Success(var id) -> jobId = id;
            case RunJobPort.Result.AlreadyRunning() -> {
                return new ProcessResult.Skipped();
            }
            case RunJobPort.Result.Failure(var cause) -> {
                transaction.rollback();
                return new ProcessResult.Failed("Failed to run job for service " + service.id(), cause);
            }
        }

        for (ConnectionType connectionType : connectionTypes) {
            var addResult = addJobStepPort.addStep(jobId, connectionType);
            switch (addResult) {
                case AddJobStepPort.Result.Success(var _) -> {
                }
                case AddJobStepPort.Result.Failure(var cause) -> {
                    transaction.rollback();
                    return new ProcessResult.Failed("Failed to add job step " + connectionType + " for job " + jobId, cause);
                }
            }
        }

        return new ProcessResult.Done();
    }

    private sealed interface ProcessResult {

        record Done() implements ProcessResult {
        }

        record Skipped() implements ProcessResult {
        }

        record Failed(String message, Exception cause) implements ProcessResult {
        }
    }
}
