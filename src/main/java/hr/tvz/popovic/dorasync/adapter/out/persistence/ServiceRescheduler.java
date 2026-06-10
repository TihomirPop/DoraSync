package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.RescheduleServicePort;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;

@Repository
public class ServiceRescheduler implements RescheduleServicePort {

    private static final Logger log = LoggerFactory.getLogger(ServiceRescheduler.class);

    private final DSLContext dsl;

    public ServiceRescheduler(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result reschedule(Id serviceId, Instant nextSyncAt) {
        try {
            dsl.update(SERVICES)
                    .set(SERVICES.NEXT_SYNC_AT, OffsetDateTime.ofInstant(nextSyncAt, ZoneOffset.UTC))
                    .where(SERVICES.ID.eq(serviceId.value()))
                    .execute();

            return new Result.Success();

        } catch (DataAccessException e) {
            log.error("Failed to reschedule service {}", serviceId, e);
            return new Result.Failure(e);
        }
    }
}
