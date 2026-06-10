package hr.tvz.popovic.dorasync.application.port.in;

import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.util.List;

public interface EnqueueScheduledJobsUseCase {

    Result enqueue();

    sealed interface Result {

        record Success(List<Id> skippedServiceIds) implements Result {
        }

        record Failure(String message, Exception cause) implements Result {
        }
    }

}
