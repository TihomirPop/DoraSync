package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.ExternalReference;
import hr.tvz.popovic.dorasync.application.domain.model.Id;

public interface FetchConnectionPort {

    Result fetch(Id jobId, ConnectionType type);

    sealed interface Result {

        record Success(Id serviceId, Id connectionId, ExternalReference externalReference) implements Result {
        }

        record NotFound() implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
