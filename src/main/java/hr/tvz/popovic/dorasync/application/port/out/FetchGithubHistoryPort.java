package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Commit;
import hr.tvz.popovic.dorasync.application.domain.model.CommitCursor;
import hr.tvz.popovic.dorasync.application.domain.model.FullName;

import java.util.List;

public interface FetchGithubHistoryPort {

    Result fetch(FullName fullName, CommitCursor cursor);

    sealed interface Result {

        record Success(String defaultBranch, List<Commit> commits) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }
}
