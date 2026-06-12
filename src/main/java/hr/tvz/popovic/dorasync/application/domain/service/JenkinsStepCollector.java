package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.Build;
import hr.tvz.popovic.dorasync.application.domain.model.BuildCursor;
import hr.tvz.popovic.dorasync.application.domain.model.StepResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobPath;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchJenkinsBuildsPort;
import hr.tvz.popovic.dorasync.application.port.out.JenkinsRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.List;

public final class JenkinsStepCollector implements StepProcessor<JobStep.CollectJenkinsStep> {

    private final FetchConnectionPort fetchConnectionPort;
    private final FetchJenkinsBuildsPort fetchJenkinsBuildsPort;
    private final JenkinsRepositoryPort jenkinsRepositoryPort;
    private final TransactionRunnerPort transactionRunner;

    public JenkinsStepCollector(
            FetchConnectionPort fetchConnectionPort,
            FetchJenkinsBuildsPort fetchJenkinsBuildsPort,
            JenkinsRepositoryPort jenkinsRepositoryPort,
            TransactionRunnerPort transactionRunner
    ) {
        this.fetchConnectionPort = fetchConnectionPort;
        this.fetchJenkinsBuildsPort = fetchJenkinsBuildsPort;
        this.jenkinsRepositoryPort = jenkinsRepositoryPort;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public StepResult process(JobStep.CollectJenkinsStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.JENKINS)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    collect(connectionId, JobPath.of(externalReference));
            case FetchConnectionPort.Result.NotFound() ->
                    new StepResult.Failure(new IllegalStateException("No JENKINS connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult collect(Id connectionId, JobPath jobPath) {
        BuildCursor cursor;
        switch (jenkinsRepositoryPort.findLatestBuildNumber(connectionId)) {
            case JenkinsRepositoryPort.FindCursorResult.Success(var foundCursor) -> cursor = foundCursor;
            case JenkinsRepositoryPort.FindCursorResult.Failure(var cause) -> {
                return new StepResult.Failure(cause);
            }
        }

        return switch (fetchJenkinsBuildsPort.fetch(jobPath, cursor)) {
            case FetchJenkinsBuildsPort.Result.Success(var builds) -> persist(connectionId, builds);
            case FetchJenkinsBuildsPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult persist(Id connectionId, List<Build> builds) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            StepResult result = switch (jenkinsRepositoryPort.upsertPipeline(connectionId)) {
                case JenkinsRepositoryPort.UpsertPipelineResult.Success(var pipelineId) ->
                        switch (jenkinsRepositoryPort.saveBuilds(pipelineId, builds)) {
                            case JenkinsRepositoryPort.SaveBuildsResult.Success(var savedCount) -> new StepResult.Success();
                            case JenkinsRepositoryPort.SaveBuildsResult.Failure(var cause) -> new StepResult.Failure(cause);
                        };
                case JenkinsRepositoryPort.UpsertPipelineResult.Failure(var cause) -> new StepResult.Failure(cause);
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
