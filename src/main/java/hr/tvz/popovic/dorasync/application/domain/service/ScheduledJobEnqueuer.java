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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public final class ScheduledJobEnqueuer implements EnqueueScheduledJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobEnqueuer.class);
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
                    log.error("Failed to fetch scheduled services", cause);
                    transaction.rollback();
                    yield new Result.Failure(cause);
                }
            };
        });

        return switch (transactionResult) {
            case TransactionRunnerPort.Result.Success<Result>(var result) -> result;
            case TransactionRunnerPort.Result.Failure<Result>(var cause) -> new Result.Failure(cause);
        };
    }

    private Result processServices(List<Service> services, TransactionRunnerPort.Transaction transaction) {
        for (Service service : services) {
            Result result = processService(service, transaction);
            switch (result) {
                case Result.Failure _ -> {
                    return result;
                }
                case Result.Success _ -> {
                }
            }
        }
        return new Result.Success();
    }

    private Result processService(Service service, TransactionRunnerPort.Transaction transaction) {
        Service rescheduled = service.reschedule();

        var rescheduleResult = rescheduleServicePort.reschedule(service.id(), rescheduled.nextSyncAt());
        switch (rescheduleResult) {
            case RescheduleServicePort.Result.Success() -> {
            }
            case RescheduleServicePort.Result.Failure(var cause) -> {
                log.error("Failed to reschedule service {}", service.id(), cause);
                transaction.rollback();
                return new Result.Failure(cause);
            }
        }

        var connectionsResult = fetchServiceConnectionsPort.fetchConnections(service.id());
        Set<ConnectionType> connectionTypes;
        switch (connectionsResult) {
            case FetchServiceConnectionsPort.Result.Success(var types) -> connectionTypes = types;
            case FetchServiceConnectionsPort.Result.Failure(var cause) -> {
                log.error("Failed to fetch connections for service {}", service.id(), cause);
                transaction.rollback();
                return new Result.Failure(cause);
            }
        }

        if (connectionTypes.isEmpty()) {
            return new Result.Success();
        }

        var runResult = runJobPort.run(service.id(), LockedUntil.nowPlusTenMinutes());
        Id jobId;
        switch (runResult) {
            case RunJobPort.Result.Success(var id) -> jobId = id;
            case RunJobPort.Result.Failure(var cause) -> {
                log.error("Failed to run job for service {}", service.id(), cause);
                transaction.rollback();
                return new Result.Failure(cause);
            }
        }

        for (ConnectionType connectionType : connectionTypes) {
            var addResult = addJobStepPort.addStep(jobId, connectionType);
            switch (addResult) {
                case AddJobStepPort.Result.Success(var _) -> {
                }
                case AddJobStepPort.Result.Failure(var cause) -> {
                    log.error("Failed to add job step {} for job {}", connectionType, jobId, cause);
                    transaction.rollback();
                    return new Result.Failure(cause);
                }
            }
        }

        return new Result.Success();
    }
}
