package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.CollectResult;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.in.WorkJobStepsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.DequeueJobStepsPort;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobPort;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobStepPort;
import hr.tvz.popovic.dorasync.application.port.out.TaskExecutorPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobStepWorker implements WorkJobStepsUseCase {

    private static final Logger log = LoggerFactory.getLogger(JobStepWorker.class);

    private final int batchSize;
    private final TransactionRunnerPort transactionRunner;
    private final DequeueJobStepsPort dequeueJobStepsPort;
    private final FinishJobStepPort finishJobStepPort;
    private final FinishJobPort finishJobPort;
    private final TaskExecutorPort taskExecutorPort;
    private final GithubStepCollector githubStepCollector;
    private final JenkinsStepCollector jenkinsStepCollector;
    private final DeploykoStepCollector deploykoStepCollector;

    public JobStepWorker(
            int batchSize,
            TransactionRunnerPort transactionRunner,
            DequeueJobStepsPort dequeueJobStepsPort,
            FinishJobStepPort finishJobStepPort,
            FinishJobPort finishJobPort,
            TaskExecutorPort taskExecutorPort,
            GithubStepCollector githubStepCollector,
            JenkinsStepCollector jenkinsStepCollector,
            DeploykoStepCollector deploykoStepCollector
    ) {
        this.batchSize = batchSize;
        this.transactionRunner = transactionRunner;
        this.dequeueJobStepsPort = dequeueJobStepsPort;
        this.finishJobStepPort = finishJobStepPort;
        this.finishJobPort = finishJobPort;
        this.taskExecutorPort = taskExecutorPort;
        this.githubStepCollector = githubStepCollector;
        this.jenkinsStepCollector = jenkinsStepCollector;
        this.deploykoStepCollector = deploykoStepCollector;
    }

    @Override
    public Result work() {
        var dequeueResult = dequeueJobStepsPort.dequeue(batchSize);

        return switch (dequeueResult) {
            case DequeueJobStepsPort.Result.Success(var jobSteps) -> {
                jobSteps.forEach(jobStep -> taskExecutorPort.execute(() -> process(jobStep)));
                yield new Result.Success(jobSteps.size());
            }
            case DequeueJobStepsPort.Result.Failure(var cause) -> new Result.Failure(cause);
        };
    }

    private void process(JobStep jobStep) {
        switch (collect(jobStep)) {
            case CollectResult.Success() -> finishStep(jobStep);
            case CollectResult.Failure(var cause) -> failStep(jobStep, cause);
        }
    }

    private CollectResult collect(JobStep jobStep) {
        return switch (jobStep) {
            case JobStep.CollectGithubStep step -> githubStepCollector.collect(step);
            case JobStep.CollectJenkinsStep step -> jenkinsStepCollector.collect(step);
            case JobStep.CollectDeploykoStep step -> deploykoStepCollector.collect(step);
        };
    }

    private void finishStep(JobStep jobStep) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            switch (finishJobStepPort.succeed(jobStep.id())) {
                case FinishJobStepPort.SucceedResult.Success(var jobId) -> {
                    switch (finishJobPort.succeedIfAllStepsSucceeded(jobId)) {
                        case FinishJobPort.Result.Success() -> {
                        }
                        case FinishJobPort.Result.Failure(var cause) -> {
                            transaction.rollback();
                            log.error("Failed to finish job {} after step {} succeeded", jobId, jobStep.id(), cause);
                        }
                    }
                }
                case FinishJobStepPort.SucceedResult.NotRunning() -> {
                    transaction.rollback();
                    log.warn("Job step {} was no longer running when finishing; skipping", jobStep.id());
                }
                case FinishJobStepPort.SucceedResult.Failure(var cause) -> {
                    transaction.rollback();
                    log.error("Failed to mark job step {} as succeeded", jobStep.id(), cause);
                }
            }
            return null;
        });

        if (transactionResult instanceof TransactionRunnerPort.Result.Failure<?>(Exception cause)) {
            log.error("Transaction failed while finishing job step {}", jobStep.id(), cause);
        }
    }

    private void failStep(JobStep jobStep, Exception collectCause) {
        log.warn("Job step {} failed during collection; marking step and job as failed", jobStep.id(), collectCause);

        var transactionResult = transactionRunner.inTransaction(transaction -> {
            switch (finishJobStepPort.fail(jobStep.id())) {
                case FinishJobStepPort.FailResult.Success() -> {
                    switch (finishJobPort.fail(jobStep.jobId())) {
                        case FinishJobPort.Result.Success() -> {
                        }
                        case FinishJobPort.Result.Failure(var cause) -> {
                            transaction.rollback();
                            log.error("Failed to mark job {} as failed", jobStep.jobId(), cause);
                        }
                    }
                }
                case FinishJobStepPort.FailResult.Failure(var cause) -> {
                    transaction.rollback();
                    log.error("Failed to mark job step {} as failed", jobStep.id(), cause);
                }
            }
            return null;
        });

        if (transactionResult instanceof TransactionRunnerPort.Result.Failure<?>(Exception cause)) {
            log.error("Transaction failed while failing job step {}", jobStep.id(), cause);
        }
    }
}
