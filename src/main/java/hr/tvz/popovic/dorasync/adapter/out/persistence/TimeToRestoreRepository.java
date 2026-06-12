package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.DeploymentStatus;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.RestoredFailure;
import hr.tvz.popovic.dorasync.application.domain.model.TerminalDeployment;
import hr.tvz.popovic.dorasync.application.port.out.TimeToRestoreRepositoryPort;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.DeploymentTargets.DEPLOYMENT_TARGETS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Deployments.DEPLOYMENTS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.TimeToRestore.TIME_TO_RESTORE;

@Repository
public class TimeToRestoreRepository implements TimeToRestoreRepositoryPort {

    private final DSLContext dsl;

    public TimeToRestoreRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public LoadResult loadUnpairedTerminalDeployments(Id serviceConnectionId) {
        try {
            var terminalDeployments = dsl
                    .select(DEPLOYMENTS.ID, DEPLOYMENTS.DEPLOYMENT_TARGET_ID, DEPLOYMENTS.STATUS, DEPLOYMENTS.RECORDED_AT)
                    .from(DEPLOYMENTS)
                    .join(DEPLOYMENT_TARGETS).on(DEPLOYMENTS.DEPLOYMENT_TARGET_ID.eq(DEPLOYMENT_TARGETS.ID))
                    .where(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID.eq(serviceConnectionId.value()))
                    .and(DEPLOYMENTS.STATUS.in(DeploymentStatus.SUCCESS.name(), DeploymentStatus.FAILURE.name()))
                    .andNotExists(dsl.selectOne()
                            .from(TIME_TO_RESTORE)
                            .where(TIME_TO_RESTORE.FAILED_DEPLOYMENT_ID.eq(DEPLOYMENTS.ID)))
                    .orderBy(DEPLOYMENTS.RECORDED_AT, DEPLOYMENTS.ID)
                    .fetch(record -> new TerminalDeployment(
                            new Id(record.get(DEPLOYMENTS.ID)),
                            new Id(record.get(DEPLOYMENTS.DEPLOYMENT_TARGET_ID)),
                            DeploymentStatus.from(record.get(DEPLOYMENTS.STATUS)),
                            record.get(DEPLOYMENTS.RECORDED_AT).toInstant()
                    ));

            return new LoadResult.Success(terminalDeployments);

        } catch (DataAccessException e) {
            return new LoadResult.Failure(e);
        }
    }

    @Override
    public SaveResult save(List<RestoredFailure> restoredFailures) {
        if (restoredFailures.isEmpty()) {
            return new SaveResult.Success(0);
        }

        try {
            List<Query> inserts = restoredFailures.stream()
                    .map(this::insert)
                    .toList();

            int[] inserted = dsl.batch(inserts).execute();

            return new SaveResult.Success(Arrays.stream(inserted).sum());

        } catch (DataAccessException e) {
            return new SaveResult.Failure(e);
        }
    }

    private Query insert(RestoredFailure restoredFailure) {
        return dsl.insertInto(TIME_TO_RESTORE)
                .columns(
                        TIME_TO_RESTORE.FAILED_DEPLOYMENT_ID,
                        TIME_TO_RESTORE.RESTORED_DEPLOYMENT_ID,
                        TIME_TO_RESTORE.DEPLOYMENT_TARGET_ID,
                        TIME_TO_RESTORE.RESTORED_AFTER_SECONDS,
                        TIME_TO_RESTORE.INCIDENT_START
                )
                .values(
                        restoredFailure.failedDeploymentId().value(),
                        restoredFailure.restoredDeploymentId().value(),
                        restoredFailure.deploymentTargetId().value(),
                        restoredFailure.restoredAfter().toSeconds(),
                        restoredFailure.incidentStart()
                )
                .onConflict(TIME_TO_RESTORE.FAILED_DEPLOYMENT_ID)
                .doNothing();
    }
}
