package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.JobStep;

import java.util.List;

public interface DequeueJobStepsPort {

    Result dequeue(int batchSize);

    sealed interface Result {

        record Success(List<JobStep> jobSteps) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
