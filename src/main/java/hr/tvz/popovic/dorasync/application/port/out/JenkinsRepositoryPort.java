package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Build;
import hr.tvz.popovic.dorasync.application.domain.model.BuildCursor;
import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.util.List;

public interface JenkinsRepositoryPort {

    FindCursorResult findLatestBuildNumber(Id serviceConnectionId);

    UpsertPipelineResult upsertPipeline(Id serviceConnectionId);

    SaveBuildsResult saveBuilds(Id pipelineId, List<Build> builds);

    sealed interface FindCursorResult {

        record Success(BuildCursor cursor) implements FindCursorResult {
        }

        record Failure(Exception cause) implements FindCursorResult {
        }
    }

    sealed interface UpsertPipelineResult {

        record Success(Id pipelineId) implements UpsertPipelineResult {
        }

        record Failure(Exception cause) implements UpsertPipelineResult {
        }
    }

    sealed interface SaveBuildsResult {

        record Success(int savedCount) implements SaveBuildsResult {
        }

        record Failure(Exception cause) implements SaveBuildsResult {
        }
    }
}
