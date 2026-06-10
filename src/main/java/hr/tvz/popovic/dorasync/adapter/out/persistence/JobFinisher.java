package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobPort;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;

@Repository
public class JobFinisher implements FinishJobPort {

    private final DSLContext dsl;

    public JobFinisher(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result succeedIfAllStepsSucceeded(Id jobId) {
        try {
            dsl.update(JOBS)
                    .set(JOBS.STATUS, JobStatus.SUCCESS)
                    .where(JOBS.ID.eq(jobId.value()))
                    .and(JOBS.STATUS.eq(JobStatus.RUNNING))
                    .andNotExists(
                            dsl.selectOne()
                                    .from(JOB_STEPS)
                                    .where(JOB_STEPS.JOB_ID.eq(jobId.value()))
                                    .and(JOB_STEPS.STATUS.ne(JobStepStatus.SUCCESS))
                    )
                    .execute();

            return new Result.Success();

        } catch (DataAccessException e) {
            return new Result.Failure(e);
        }
    }

    @Override
    public Result fail(Id jobId) {
        try {
            dsl.update(JOBS)
                    .set(JOBS.STATUS, JobStatus.FAILURE)
                    .where(JOBS.ID.eq(jobId.value()))
                    .and(JOBS.STATUS.eq(JobStatus.RUNNING))
                    .execute();

            return new Result.Success();

        } catch (DataAccessException e) {
            return new Result.Failure(e);
        }
    }
}
