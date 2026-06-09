package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.port.in.EnqueueScheduledJobsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScheduledJobEnqueuer implements EnqueueScheduledJobsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobEnqueuer.class);
    private final TransactionRunnerPort transactionRunner;
    private final FetchScheduledServicesPort fetchScheduledServicesPort;

    public ScheduledJobEnqueuer(TransactionRunnerPort transactionRunner, FetchScheduledServicesPort fetchScheduledServicesPort) {
        this.transactionRunner = transactionRunner;
        this.fetchScheduledServicesPort = fetchScheduledServicesPort;
    }

    @Override
    public void enqueue() {
        transactionRunner.inTransaction(transaction -> {
            return switch (fetchScheduledServicesPort.fetchScheduledServices()) {
                case FetchScheduledServicesPort.Result.Success success -> {
                    log.info("Fetched scheduled services:");
                    success.services().forEach(service -> log.info(service.toString()));
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
