package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Build;
import hr.tvz.popovic.dorasync.application.domain.model.BuildCursor;
import hr.tvz.popovic.dorasync.application.domain.model.JobPath;

import java.util.List;

public interface FetchJenkinsBuildsPort {

    Result fetch(JobPath jobPath, BuildCursor cursor);

    sealed interface Result {

        record Success(List<Build> builds) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }
}
