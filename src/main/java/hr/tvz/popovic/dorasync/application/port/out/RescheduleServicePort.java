package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.time.Instant;

public interface RescheduleServicePort {

    Result reschedule(Id serviceId, Instant nextSyncAt);

    sealed interface Result {

        record Success() implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
