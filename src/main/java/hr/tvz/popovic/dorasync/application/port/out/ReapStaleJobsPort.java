package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.time.Instant;
import java.util.List;

public interface ReapStaleJobsPort {

    ReapJobsResult reapJobs(Instant now);

    ReapJobStepsResult reapJobSteps(List<Id> jobIds);

    sealed interface ReapJobsResult {

        record Success(List<Id> jobIds) implements ReapJobsResult {
        }

        record Failure(Exception cause) implements ReapJobsResult {
        }
    }

    sealed interface ReapJobStepsResult {

        record Success(int reapedCount) implements ReapJobStepsResult {
        }

        record Failure(Exception cause) implements ReapJobStepsResult {
        }
    }

}
