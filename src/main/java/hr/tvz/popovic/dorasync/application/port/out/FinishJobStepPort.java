package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Id;

public interface FinishJobStepPort {

    SucceedResult succeed(Id jobStepId);

    FailResult fail(Id jobStepId);

    sealed interface SucceedResult {

        record Success(Id jobId) implements SucceedResult {
        }

        record NotRunning() implements SucceedResult {
        }

        record Failure(Exception cause) implements SucceedResult {
        }
    }

    sealed interface FailResult {

        record Success() implements FailResult {
        }

        record Failure(Exception cause) implements FailResult {
        }
    }

}
