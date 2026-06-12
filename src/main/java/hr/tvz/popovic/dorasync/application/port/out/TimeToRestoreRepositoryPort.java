package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.RestoredFailure;
import hr.tvz.popovic.dorasync.application.domain.model.TerminalDeployment;

import java.util.List;

public interface TimeToRestoreRepositoryPort {

    LoadResult loadUnpairedTerminalDeployments(Id serviceConnectionId);

    SaveResult save(List<RestoredFailure> restoredFailures);

    sealed interface LoadResult {

        record Success(List<TerminalDeployment> terminalDeployments) implements LoadResult {
        }

        record Failure(Exception cause) implements LoadResult {
        }
    }

    sealed interface SaveResult {

        record Success(int savedCount) implements SaveResult {
        }

        record Failure(Exception cause) implements SaveResult {
        }
    }
}
