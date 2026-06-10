package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.ServiceConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.FetchServiceConnectionsPort;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Set;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;

@Repository
public class ServiceConnectionsFetcher implements FetchServiceConnectionsPort {

    private static final Logger log = LoggerFactory.getLogger(ServiceConnectionsFetcher.class);

    private final DSLContext dsl;

    public ServiceConnectionsFetcher(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result fetchConnections(Id serviceId) {
        try {
            Set<ConnectionType> connectionTypes = dsl.selectDistinct(SERVICE_CONNECTIONS.TYPE)
                    .from(SERVICE_CONNECTIONS)
                    .where(SERVICE_CONNECTIONS.SERVICE_ID.eq(serviceId.value()))
                    .fetchSet(record -> toConnectionType(record.value1()));

            return new Result.Success(connectionTypes);

        } catch (DataAccessException e) {
            log.error("Failed to fetch connections for service {}", serviceId, e);
            return new Result.Failure(e);
        }
    }

    private static ConnectionType toConnectionType(ServiceConnectionType type) {
        return switch (type) {
            case GITHUB -> ConnectionType.GITHUB;
            case JENKINS -> ConnectionType.JENKINS;
            case DEPLOYKO -> ConnectionType.DEPLOYKO;
        };
    }
}
