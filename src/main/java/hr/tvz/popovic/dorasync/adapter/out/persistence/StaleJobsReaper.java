package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.ReapStaleJobsPort;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;

@Repository
public class StaleJobsReaper implements ReapStaleJobsPort {

    private final DSLContext dsl;

    public StaleJobsReaper(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ReapJobsResult reapJobs(Instant now) {
        try {
            var jobIds = dsl.update(JOBS)
                    .set(JOBS.STATUS, JobStatus.FAILURE)
                    .where(JOBS.STATUS.eq(JobStatus.RUNNING))
                    .and(JOBS.LOCKED_UNTIL.lessThan(OffsetDateTime.ofInstant(now, ZoneOffset.UTC)))
                    .returning(JOBS.ID)
                    .fetch(record -> new Id(record.getId()));

            return new ReapJobsResult.Success(jobIds);

        } catch (DataAccessException e) {
            return new ReapJobsResult.Failure(e);
        }
    }

    @Override
    public ReapJobStepsResult reapJobSteps(List<Id> jobIds) {
        try {
            var ids = jobIds.stream().map(Id::value).toList();

            int reapedCount = dsl.update(JOB_STEPS)
                    .set(JOB_STEPS.STATUS, JobStepStatus.FAILURE)
                    .where(JOB_STEPS.JOB_ID.in(ids))
                    .and(JOB_STEPS.STATUS.in(JobStepStatus.PENDING, JobStepStatus.RUNNING))
                    .execute();

            return new ReapJobStepsResult.Success(reapedCount);

        } catch (DataAccessException e) {
            return new ReapJobStepsResult.Failure(e);
        }
    }
}
