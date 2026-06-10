package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Id;

public interface FinishJobPort {

    Result succeedIfAllStepsSucceeded(Id jobId);

    Result fail(Id jobId);

    sealed interface Result {

        record Success() implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
