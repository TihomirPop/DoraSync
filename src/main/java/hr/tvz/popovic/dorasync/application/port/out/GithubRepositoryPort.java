package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Commit;
import hr.tvz.popovic.dorasync.application.domain.model.CommitCursor;
import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.util.List;

public interface GithubRepositoryPort {

    FindCursorResult findLatestCommittedAt(Id serviceConnectionId);

    UpsertRepositoryResult upsertRepository(Id serviceConnectionId, String defaultBranch);

    SaveCommitsResult saveCommits(Id repositoryId, List<Commit> commits);

    sealed interface FindCursorResult {

        record Success(CommitCursor cursor) implements FindCursorResult {
        }

        record Failure(Exception cause) implements FindCursorResult {
        }
    }

    sealed interface UpsertRepositoryResult {

        record Success(Id repositoryId) implements UpsertRepositoryResult {
        }

        record Failure(Exception cause) implements UpsertRepositoryResult {
        }
    }

    sealed interface SaveCommitsResult {

        record Success(int savedCount) implements SaveCommitsResult {
        }

        record Failure(Exception cause) implements SaveCommitsResult {
        }
    }
}
