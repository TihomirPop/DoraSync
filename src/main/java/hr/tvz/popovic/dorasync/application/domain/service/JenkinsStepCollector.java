package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.Build;
import hr.tvz.popovic.dorasync.application.domain.model.BuildCursor;
import hr.tvz.popovic.dorasync.application.domain.model.CollectResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobPath;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchJenkinsBuildsPort;
import hr.tvz.popovic.dorasync.application.port.out.JenkinsRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.List;

public final class JenkinsStepCollector {

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

    public CollectResult collect(JobStep.CollectJenkinsStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.JENKINS)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    collect(connectionId, JobPath.of(externalReference));
            case FetchConnectionPort.Result.NotFound() ->
                    new CollectResult.Failure(new IllegalStateException("No JENKINS connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }

    private CollectResult collect(Id connectionId, JobPath jobPath) {
        BuildCursor cursor;
        switch (jenkinsRepositoryPort.findLatestBuildNumber(connectionId)) {
            case JenkinsRepositoryPort.FindCursorResult.Success(var foundCursor) -> cursor = foundCursor;
            case JenkinsRepositoryPort.FindCursorResult.Failure(var cause) -> {
                return new CollectResult.Failure(cause);
            }
        }

        return switch (fetchJenkinsBuildsPort.fetch(jobPath, cursor)) {
            case FetchJenkinsBuildsPort.Result.Success(var builds) -> persist(connectionId, jobPath, builds);
            case FetchJenkinsBuildsPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }

    private CollectResult persist(Id connectionId, JobPath jobPath, List<Build> builds) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            CollectResult result = switch (jenkinsRepositoryPort.upsertPipeline(connectionId, jobPath)) {
                case JenkinsRepositoryPort.UpsertPipelineResult.Success(var pipelineId) ->
                        switch (jenkinsRepositoryPort.saveBuilds(pipelineId, builds)) {
                            case JenkinsRepositoryPort.SaveBuildsResult.Success(var savedCount) -> new CollectResult.Success();
                            case JenkinsRepositoryPort.SaveBuildsResult.Failure(var cause) -> new CollectResult.Failure(cause);
                        };
                case JenkinsRepositoryPort.UpsertPipelineResult.Failure(var cause) -> new CollectResult.Failure(cause);
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
