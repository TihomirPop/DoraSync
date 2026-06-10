package hr.tvz.popovic.dorasync.application.port.in;

public interface WorkJobStepsUseCase {

    Result work();

    sealed interface Result {

        record Success(int claimedCount) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
