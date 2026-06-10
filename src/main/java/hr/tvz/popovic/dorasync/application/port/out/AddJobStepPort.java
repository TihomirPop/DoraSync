package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;

public interface AddJobStepPort {

    Result addStep(Id jobId, ConnectionType connectionType);

    sealed interface Result {

        record Success(Id jobStepId) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
