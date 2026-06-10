package hr.tvz.popovic.dorasync.application.port.in;

public interface EnqueueScheduledJobsUseCase {

    Result enqueue();

    sealed interface Result {

        record Success() implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
