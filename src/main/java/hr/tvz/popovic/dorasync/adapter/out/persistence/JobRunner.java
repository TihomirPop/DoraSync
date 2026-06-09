package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.LockedUntil;
import hr.tvz.popovic.dorasync.application.port.out.RunJobPort;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;

@Repository
public class JobRunner implements RunJobPort {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private final DSLContext dsl;

    public JobRunner(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result run(Id serviceId, LockedUntil lockedUntil) {
        try {
            var record = dsl.insertInto(JOBS)
                    .columns(JOBS.SERVICE_ID, JOBS.STATUS, JOBS.LOCKED_UNTIL)
                    .values(
                            serviceId.value(),
                            JobStatus.RUNNING,
                            OffsetDateTime.ofInstant(lockedUntil.value(), ZoneOffset.UTC)
                    )
                    .returning(JOBS.ID)
                    .fetchOne();

            if (record == null) {
                return new Result.Failure(new IllegalStateException("Insert returned no record"));
            }

            return new Result.Success(new Id(record.getId()));

        } catch (DuplicateKeyException e) {
            log.info("Job already running for service {}", serviceId);
            return new Result.Failure(e);
        } catch (DataAccessException e) {
            log.error("Failed to create running job for service {}", serviceId, e);
            return new Result.Failure(e);
        }
    }
}

