package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.application.port.out.ReapStaleJobsPort;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;

@Repository
public class StaleJobsReaper implements ReapStaleJobsPort {

    private final DSLContext dsl;

    public StaleJobsReaper(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result reap(Instant now) {
        try {
            int reapedCount = dsl.update(JOBS)
                    .set(JOBS.STATUS, JobStatus.FAILURE)
                    .where(JOBS.STATUS.eq(JobStatus.RUNNING))
                    .and(JOBS.LOCKED_UNTIL.lessThan(OffsetDateTime.ofInstant(now, ZoneOffset.UTC)))
                    .execute();

            return new Result.Success(reapedCount);

        } catch (DataAccessException e) {
            return new Result.Failure(e);
        }
    }
}
