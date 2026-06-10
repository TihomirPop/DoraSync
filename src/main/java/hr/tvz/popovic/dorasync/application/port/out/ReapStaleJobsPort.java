package hr.tvz.popovic.dorasync.application.port.out;

import java.time.Instant;

public interface ReapStaleJobsPort {

    Result reap(Instant now);

    sealed interface Result {

        record Success(int reapedCount) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
