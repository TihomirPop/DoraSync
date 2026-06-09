package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.LockedUntil;

public interface RunJobPort {

    Result run(Id serviceId, LockedUntil lockedUntil);

    sealed interface Result {

        record Success(Id jobId) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}

