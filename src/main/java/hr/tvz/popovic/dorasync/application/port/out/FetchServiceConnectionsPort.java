package hr.tvz.popovic.dorasync.application.port.out;

import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;

import java.util.Set;

public interface FetchServiceConnectionsPort {

    Result fetchConnections(Id serviceId);

    sealed interface Result {

        record Success(Set<ConnectionType> connectionTypes) implements Result {
        }

        record Failure(Exception cause) implements Result {
        }
    }

}
