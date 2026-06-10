package hr.tvz.popovic.dorasync.application.port.in;

public interface ReapStaleJobsUseCase {

    Result reap();

    sealed interface Result {

        record Success(int reapedCount) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
