package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.AddJobStepPort;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepType;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;

@Repository
public class JobStepAdder implements AddJobStepPort {

    private final DSLContext dsl;

    public JobStepAdder(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Result addStep(Id jobId, ConnectionType connectionType) {
        return insert(jobId, toJobStepType(connectionType));
    }

    @Override
    public Result addComputeMetricsStep(Id jobId) {
        return insert(jobId, JobStepType.COMPUTE_METRICS);
    }

    private Result insert(Id jobId, JobStepType type) {
        try {
            var record = dsl.insertInto(JOB_STEPS)
                    .columns(JOB_STEPS.JOB_ID, JOB_STEPS.TYPE, JOB_STEPS.STATUS)
                    .values(jobId.value(), type, JobStepStatus.PENDING)
                    .returning(JOB_STEPS.ID)
                    .fetchOne();

            if (record == null) {
                return new Result.Failure(new IllegalStateException("Insert returned no record"));
            }

            return new Result.Success(new Id(record.getId()));

        } catch (DataAccessException e) {
            return new Result.Failure(e);
        }
    }

    private static JobStepType toJobStepType(ConnectionType connectionType) {
        return switch (connectionType) {
            case GITHUB -> JobStepType.COLLECT_GITHUB;
            case JENKINS -> JobStepType.COLLECT_JENKINS;
            case DEPLOYKO -> JobStepType.COLLECT_DEPLOYKO;
        };
    }
}
