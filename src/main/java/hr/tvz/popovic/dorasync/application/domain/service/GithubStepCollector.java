package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.Commit;
import hr.tvz.popovic.dorasync.application.domain.model.CommitCursor;
import hr.tvz.popovic.dorasync.application.domain.model.StepResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.FullName;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchGithubHistoryPort;
import hr.tvz.popovic.dorasync.application.port.out.GithubRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.List;

public final class GithubStepCollector implements StepProcessor<JobStep.CollectGithubStep> {

    private final FetchConnectionPort fetchConnectionPort;
    private final FetchGithubHistoryPort fetchGithubHistoryPort;
    private final GithubRepositoryPort githubRepositoryPort;
    private final TransactionRunnerPort transactionRunner;

    public GithubStepCollector(
            FetchConnectionPort fetchConnectionPort,
            FetchGithubHistoryPort fetchGithubHistoryPort,
            GithubRepositoryPort githubRepositoryPort,
            TransactionRunnerPort transactionRunner
    ) {
        this.fetchConnectionPort = fetchConnectionPort;
        this.fetchGithubHistoryPort = fetchGithubHistoryPort;
        this.githubRepositoryPort = githubRepositoryPort;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public StepResult process(JobStep.CollectGithubStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.GITHUB)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    collect(connectionId, FullName.of(externalReference));
            case FetchConnectionPort.Result.NotFound() ->
                    new StepResult.Failure(new IllegalStateException("No GITHUB connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult collect(Id connectionId, FullName fullName) {
        CommitCursor cursor;
        switch (githubRepositoryPort.findLatestCommittedAt(connectionId)) {
            case GithubRepositoryPort.FindCursorResult.Success(var foundCursor) -> cursor = foundCursor;
            case GithubRepositoryPort.FindCursorResult.Failure(var cause) -> {
                return new StepResult.Failure(cause);
            }
        }

        return switch (fetchGithubHistoryPort.fetch(fullName, cursor)) {
            case FetchGithubHistoryPort.Result.Success(var defaultBranch, var commits) ->
                    persist(connectionId, fullName, defaultBranch, commits);
            case FetchGithubHistoryPort.Result.Failure(var cause) -> new StepResult.Failure(cause);
        };
    }

    private StepResult persist(Id connectionId, FullName fullName, String defaultBranch, List<Commit> commits) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            StepResult result = switch (githubRepositoryPort.upsertRepository(connectionId, fullName, defaultBranch)) {
                case GithubRepositoryPort.UpsertRepositoryResult.Success(var repositoryId) ->
                        switch (githubRepositoryPort.saveCommits(repositoryId, commits)) {
                            case GithubRepositoryPort.SaveCommitsResult.Success(var savedCount) -> new StepResult.Success();
                            case GithubRepositoryPort.SaveCommitsResult.Failure(var cause) -> new StepResult.Failure(cause);
                        };
                case GithubRepositoryPort.UpsertRepositoryResult.Failure(var cause) -> new StepResult.Failure(cause);
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
