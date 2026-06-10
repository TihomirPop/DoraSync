package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.Service;
import hr.tvz.popovic.dorasync.application.domain.model.ServiceName;
import hr.tvz.popovic.dorasync.application.port.out.FetchScheduledServicesPort;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;

@Repository
public class ScheduledServicesFetcher implements FetchScheduledServicesPort {

    private final DSLContext dsl;

    public ScheduledServicesFetcher(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result fetchScheduledServices() {
        try {
            List<Service> services = dsl.selectFrom(SERVICES)
                    .where(SERVICES.NEXT_SYNC_AT.lessThan(OffsetDateTime.now()))
                    .fetch(record -> new Service(
                            new Id(record.getId()),
                            new ServiceName(record.getName()),
                            record.getNextSyncAt().toInstant()
                    ));

            return new Result.Success(services);

        } catch (DataAccessException e) {
            return new Result.Failure(e);
        }
    }
}