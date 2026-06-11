package hr.tvz.popovic.dorasync.application.domain.service;

import hr.tvz.popovic.dorasync.application.domain.model.Commit;
import hr.tvz.popovic.dorasync.application.domain.model.CommitCursor;
import hr.tvz.popovic.dorasync.application.domain.model.CollectResult;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.FullName;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchGithubHistoryPort;
import hr.tvz.popovic.dorasync.application.port.out.GithubRepositoryPort;
import hr.tvz.popovic.dorasync.application.port.out.TransactionRunnerPort;

import java.util.List;

public final class GithubStepCollector {

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

    public CollectResult collect(JobStep.CollectGithubStep step) {
        return switch (fetchConnectionPort.fetch(step.jobId(), ConnectionType.GITHUB)) {
            case FetchConnectionPort.Result.Success(var serviceId, var connectionId, var externalReference) ->
                    collect(connectionId, FullName.of(externalReference));
            case FetchConnectionPort.Result.NotFound() ->
                    new CollectResult.Failure(new IllegalStateException("No GITHUB connection found for job " + step.jobId().value()));
            case FetchConnectionPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }

    private CollectResult collect(Id connectionId, FullName fullName) {
        CommitCursor cursor;
        switch (githubRepositoryPort.findLatestCommittedAt(connectionId)) {
            case GithubRepositoryPort.FindCursorResult.Success(var foundCursor) -> cursor = foundCursor;
            case GithubRepositoryPort.FindCursorResult.Failure(var cause) -> {
                return new CollectResult.Failure(cause);
            }
        }

        return switch (fetchGithubHistoryPort.fetch(fullName, cursor)) {
            case FetchGithubHistoryPort.Result.Success(var defaultBranch, var commits) ->
                    persist(connectionId, fullName, defaultBranch, commits);
            case FetchGithubHistoryPort.Result.Failure(var cause) -> new CollectResult.Failure(cause);
        };
    }

    private CollectResult persist(Id connectionId, FullName fullName, String defaultBranch, List<Commit> commits) {
        var transactionResult = transactionRunner.inTransaction(transaction -> {
            CollectResult result = switch (githubRepositoryPort.upsertRepository(connectionId, fullName, defaultBranch)) {
                case GithubRepositoryPort.UpsertRepositoryResult.Success(var repositoryId) ->
                        switch (githubRepositoryPort.saveCommits(repositoryId, commits)) {
                            case GithubRepositoryPort.SaveCommitsResult.Success(var savedCount) -> new CollectResult.Success();
                            case GithubRepositoryPort.SaveCommitsResult.Failure(var cause) -> new CollectResult.Failure(cause);
                        };
                case GithubRepositoryPort.UpsertRepositoryResult.Failure(var cause) -> new CollectResult.Failure(cause);
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
