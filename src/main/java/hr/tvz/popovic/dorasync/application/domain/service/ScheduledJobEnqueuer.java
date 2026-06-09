package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.LockedUntil;
import hr.tvz.popovic.dorasync.application.domain.model.Service;
import hr.tvz.popovic.dorasync.application.port.in.EnqueueScheduledJobsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import hr.tvz.popovic.dorasync.application.port.out.RunJobPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScheduledJobEnqueuer implements EnqueueScheduledJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobEnqueuer.class);
    private final TransactionRunnerPort transactionRunner;
    private final FetchScheduledServicesPort fetchScheduledServicesPort;
    private final RunJobPort runJobPort;

    public ScheduledJobEnqueuer(
            TransactionRunnerPort transactionRunner,
            FetchScheduledServicesPort fetchScheduledServicesPort,
            RunJobPort runJobPort
    ) {
        this.transactionRunner = transactionRunner;
        this.fetchScheduledServicesPort = fetchScheduledServicesPort;
        this.runJobPort = runJobPort;
    }

    @Override
    public void enqueue() {
        transactionRunner.inTransaction(transaction -> {
            return switch (fetchScheduledServicesPort.fetchScheduledServices()) {
                case FetchScheduledServicesPort.Result.Success success -> {
                    log.info("Fetched scheduled services:");
                    for (Service service : success.services()) {
                        log.info("- {} (ID: {})", service.name().value(), service.id().value());
                        var runJobResult = runJobPort.run(service.id(), LockedUntil.nowPlusTenMinutes());
                        if (runJobResult instanceof RunJobPort.Result.Success runJobSuccess) {
                            log.info("Successfully enqueued job for service ID {}: Job ID {}", service.id().value(), runJobSuccess.jobId().value());
                        } else if (runJobResult instanceof RunJobPort.Result.Failure runJobFailure) {
                            log.error("Failed to enqueue job for service ID {}: {}", service.id().value(), runJobFailure.cause().getMessage());
                            transaction.rollback();
                            yield new FetchScheduledServicesPort.Result.Failure(runJobFailure.cause());
                        }
                    }
                    yield success;
                }
                case FetchScheduledServicesPort.Result.Failure failure -> {
                    log.error("Failed to fetch scheduled services: {} ", failure.cause().getMessage());
                    transaction.rollback();
                    yield failure;
                }
            };
        });
    }
}
