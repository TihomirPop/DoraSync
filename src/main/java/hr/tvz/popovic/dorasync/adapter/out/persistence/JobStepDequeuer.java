package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepType;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.records.JobStepsRecord;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.out.DequeueJobStepsPort;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;

@Repository
public class JobStepDequeuer implements DequeueJobStepsPort {

    private final DSLContext dsl;

    public JobStepDequeuer(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result dequeue(int batchSize) {
        try {
            var sibling = JOB_STEPS.as("sibling");

            var jobSteps = dsl.update(JOB_STEPS)
                    .set(JOB_STEPS.STATUS, JobStepStatus.RUNNING)
                    .where(JOB_STEPS.ID.in(
                            dsl.select(JOB_STEPS.ID)
                                    .from(JOB_STEPS)
                                    .where(JOB_STEPS.STATUS.eq(JobStepStatus.PENDING))
                                    // A COMPUTE_METRICS step waits until every collect step of its job has succeeded.
                                    .and(JOB_STEPS.TYPE.ne(JobStepType.COMPUTE_METRICS)
                                            .or(DSL.notExists(
                                                    dsl.selectOne()
                                                            .from(sibling)
                                                            .where(sibling.JOB_ID.eq(JOB_STEPS.JOB_ID))
                                                            .and(sibling.TYPE.ne(JobStepType.COMPUTE_METRICS))
                                                            .and(sibling.STATUS.ne(JobStepStatus.SUCCESS)))))
                                    .orderBy(JOB_STEPS.ID)
                                    .limit(batchSize)
                                    .forUpdate()
                                    .skipLocked()
                    ))
                    .returning(JOB_STEPS.ID, JOB_STEPS.JOB_ID, JOB_STEPS.TYPE)
                    .fetch(JobStepDequeuer::toJobStep);

            return new Result.Success(jobSteps);

        } catch (DataAccessException e) {
            return new Result.Failure(e);
        }
    }

    private static JobStep toJobStep(JobStepsRecord record) {
        var id = new Id(record.getId());
        var jobId = new Id(record.getJobId());
        return switch (record.getType()) {
            case COLLECT_GITHUB -> new JobStep.CollectGithubStep(id, jobId);
            case COLLECT_JENKINS -> new JobStep.CollectJenkinsStep(id, jobId);
            case COLLECT_DEPLOYKO -> new JobStep.CollectDeploykoStep(id, jobId);
            case COMPUTE_METRICS -> new JobStep.ComputeMetricsStep(id, jobId);
        };
    }
}
