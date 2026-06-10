package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.port.in.ReapStaleJobsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.ReapStaleJobsPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.time.Instant;

public final class StaleJobReaper implements ReapStaleJobsUseCase {

    private final TransactionRunnerPort transactionRunner;
    private final ReapStaleJobsPort reapStaleJobsPort;

    public StaleJobReaper(TransactionRunnerPort transactionRunner, ReapStaleJobsPort reapStaleJobsPort) {
        this.transactionRunner = transactionRunner;
        this.reapStaleJobsPort = reapStaleJobsPort;
    }

    @Override
    public Result reap() {
        var transactionResult = transactionRunner.inTransaction(this::reapStaleJobs);

        return switch (transactionResult) {
            case TransactionRunnerPort.Result.Success<Result>(var result) -> result;
            case TransactionRunnerPort.Result.Failure<Result>(var cause) -> new Result.Failure(cause);
        };
    }

    private Result reapStaleJobs(TransactionRunnerPort.Transaction transaction) {
        var jobsResult = reapStaleJobsPort.reapJobs(Instant.now());
        return switch (jobsResult) {
            case ReapStaleJobsPort.ReapJobsResult.Success(var jobIds) -> {
                if (jobIds.isEmpty()) {
                    yield new Result.Success(0);
                }

                var stepsResult = reapStaleJobsPort.reapJobSteps(jobIds);
                yield switch (stepsResult) {
                    case ReapStaleJobsPort.ReapJobStepsResult.Success(var _) -> new Result.Success(jobIds.size());
                    case ReapStaleJobsPort.ReapJobStepsResult.Failure(var cause) -> {
                        transaction.rollback();
                        yield new Result.Failure(cause);
                    }
                };
            }
            case ReapStaleJobsPort.ReapJobsResult.Failure(var cause) -> {
                transaction.rollback();
                yield new Result.Failure(cause);
            }
        };
    }
}
