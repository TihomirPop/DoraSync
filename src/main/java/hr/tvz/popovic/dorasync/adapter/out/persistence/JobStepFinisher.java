package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobStepPort;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;

@Repository
public class JobStepFinisher implements FinishJobStepPort {

    private final DSLContext dsl;

    public JobStepFinisher(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public SucceedResult succeed(Id jobStepId) {
        try {
            var record = dsl.update(JOB_STEPS)
                    .set(JOB_STEPS.STATUS, JobStepStatus.SUCCESS)
                    .where(JOB_STEPS.ID.eq(jobStepId.value()))
                    .and(JOB_STEPS.STATUS.eq(JobStepStatus.RUNNING))
                    .returning(JOB_STEPS.JOB_ID)
                    .fetchOne();

            if (record == null) {
                return new SucceedResult.NotRunning();
            }

            return new SucceedResult.Success(new Id(record.getJobId()));

        } catch (DataAccessException e) {
            return new SucceedResult.Failure(e);
        }
    }

    @Override
    public FailResult fail(Id jobStepId) {
        try {
            dsl.update(JOB_STEPS)
                    .set(JOB_STEPS.STATUS, JobStepStatus.FAILURE)
                    .where(JOB_STEPS.ID.eq(jobStepId.value()))
                    .execute();

            return new FailResult.Success();

        } catch (DataAccessException e) {
            return new FailResult.Failure(e);
        }
    }
}
