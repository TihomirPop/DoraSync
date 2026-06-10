package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.FetchServiceConnectionsPort;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;

@Repository
public class ServiceConnectionsFetcher implements FetchServiceConnectionsPort {

    @Override
    public Result fetchConnections(Id serviceId) {
        // service_connections table doesn't exist yet; return all connection types as a placeholder.
        return new Result.Success(EnumSet.allOf(ConnectionType.class));
    }
}
