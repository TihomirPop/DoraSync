package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.BuildIdentity;
import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.CommittedChange;
import hr.tvz.popovic.dorasync.application.domain.model.DeliveredCommit;
import hr.tvz.popovic.dorasync.application.domain.model.DeployedVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.util.List;
import java.util.Set;

public interface LeadTimeRepositoryPort {

    /**
     * The undelivered commits of a service plus the SUCCESS deployments past the delivery frontier
     * (the newest deployment already referenced in {@code lead_time_for_changes}). The frontier is
     * derived from stored rows, so there is no cursor to drift.
     */
    LoadResult loadUndeliveredCommitsAndNewDeployments(Id serviceId);

    /** The build → commit SHA mapping for the given build numbers, used by the fallback resolution. */
    LoadBuildsResult loadBuilds(Id serviceId, Set<BuildNumber> buildNumbers);

    SaveResult save(List<DeliveredCommit> deliveredCommits);

    sealed interface LoadResult {

        record Success(List<CommittedChange> undeliveredCommits, List<DeployedVersion> newDeployments)
                implements LoadResult {
        }

        record Failure(Exception cause) implements LoadResult {
        }
    }

    sealed interface LoadBuildsResult {

        record Success(List<BuildIdentity> builds) implements LoadBuildsResult {
        }

        record Failure(Exception cause) implements LoadBuildsResult {
        }
    }

    sealed interface SaveResult {

        record Success(int savedCount) implements SaveResult {
        }

        record Failure(Exception cause) implements SaveResult {
        }
    }
}
