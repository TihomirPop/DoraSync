package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.ServiceConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.ExternalReference;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;

@Repository
public class ConnectionFetcher implements FetchConnectionPort {

    private final DSLContext dsl;

    public ConnectionFetcher(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result fetch(Id jobId, ConnectionType type) {
        try {
            var record = dsl.select(JOBS.SERVICE_ID, SERVICE_CONNECTIONS.EXTERNAL_REFERENCE)
                    .from(JOBS)
                    .join(SERVICE_CONNECTIONS)
                    .on(SERVICE_CONNECTIONS.SERVICE_ID.eq(JOBS.SERVICE_ID))
                    .and(SERVICE_CONNECTIONS.TYPE.eq(toServiceConnectionType(type)))
                    .where(JOBS.ID.eq(jobId.value()))
                    .fetchOne();

            if (record == null) {
                return new Result.NotFound();
            }

            return new Result.Success(new Id(record.value1()), new ExternalReference(record.value2()));

        } catch (DataAccessException e) {
            return new Result.Failure(e);
        }
    }

    private static ServiceConnectionType toServiceConnectionType(ConnectionType type) {
        return switch (type) {
            case GITHUB -> ServiceConnectionType.GITHUB;
            case JENKINS -> ServiceConnectionType.JENKINS;
            case DEPLOYKO -> ServiceConnectionType.DEPLOYKO;
        };
    }
}
