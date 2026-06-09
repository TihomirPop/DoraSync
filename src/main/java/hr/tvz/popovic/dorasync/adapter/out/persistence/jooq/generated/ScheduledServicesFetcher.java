package hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated;

import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.Service;
import hr.tvz.popovic.dorasync.application.domain.model.ServiceName;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;

@Repository
public final class ScheduledServicesFetcher implements FetchScheduledServicesPort {

    private static final Logger log = LoggerFactory.getLogger(ScheduledServicesFetcher.class);
    private final DSLContext dsl;

    public ScheduledServicesFetcher(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result fetchScheduledServices() {
        try {
            List<Service> services = dsl.selectFrom(SERVICES)
                    .where(SERVICES.NEXT_SYNC_AT.lessThan(OffsetDateTime.now()))
                    .fetch()
                    .map(record -> new Service(
                            new Id(record.getId()),
                            new ServiceName(record.getName())
                    ));

            return new Result.Success(services);
        } catch (DataAccessException e) {
            log.error("Failed to fetch scheduled services", e);
            return new Result.Failure();
        }
    }

}
