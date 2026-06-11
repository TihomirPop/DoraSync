package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.Deployment;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentCursor;
import hr.tvz.popovic.dorasync.application.domain.model.DeploykoService;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.port.out.DeploykoRepositoryPort;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.DeploymentTargets.DEPLOYMENT_TARGETS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Deployments.DEPLOYMENTS;
import static org.jooq.impl.DSL.max;

@Repository
public class DeploykoRepository implements DeploykoRepositoryPort {

    private final DSLContext dsl;

    public DeploykoRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public FindCursorResult findLatestRecordedAt(Id serviceConnectionId) {
        try {
            var latest = dsl.select(max(DEPLOYMENTS.RECORDED_AT))
                    .from(DEPLOYMENTS)
                    .join(DEPLOYMENT_TARGETS).on(DEPLOYMENTS.DEPLOYMENT_TARGET_ID.eq(DEPLOYMENT_TARGETS.ID))
                    .where(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID.eq(serviceConnectionId.value()))
                    .fetchOne(max(DEPLOYMENTS.RECORDED_AT));

            DeploymentCursor cursor = latest == null
                    ? new DeploymentCursor.Beginning()
                    : new DeploymentCursor.Since(latest.toInstant());

            return new FindCursorResult.Success(cursor);

        } catch (DataAccessException e) {
            return new FindCursorResult.Failure(e);
        }
    }

    @Override
    public UpsertTargetResult upsertTarget(Id serviceConnectionId, DeploykoService service) {
        try {
            var record = dsl.insertInto(DEPLOYMENT_TARGETS)
                    .columns(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID, DEPLOYMENT_TARGETS.DEPLOYKO_SERVICE)
                    .values(serviceConnectionId.value(), service.value())
                    .onConflict(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID)
                    .doUpdate()
                    .set(DEPLOYMENT_TARGETS.DEPLOYKO_SERVICE, service.value())
                    .returning(DEPLOYMENT_TARGETS.ID)
                    .fetchOne();

            if (record == null) {
                return new UpsertTargetResult.Failure(new IllegalStateException("Upsert returned no record"));
            }

            return new UpsertTargetResult.Success(new Id(record.getId()));

        } catch (DataAccessException e) {
            return new UpsertTargetResult.Failure(e);
        }
    }

    @Override
    public SaveDeploymentsResult saveDeployments(Id deploymentTargetId, List<Deployment> deployments) {
        if (deployments.isEmpty()) {
            return new SaveDeploymentsResult.Success(0);
        }

        try {
            List<Query> inserts = deployments.stream()
                    .map(deployment -> insert(deploymentTargetId, deployment))
                    .toList();

            int[] inserted = dsl.batch(inserts).execute();

            return new SaveDeploymentsResult.Success(Arrays.stream(inserted).sum());

        } catch (DataAccessException e) {
            return new SaveDeploymentsResult.Failure(e);
        }
    }

    private Query insert(Id deploymentTargetId, Deployment deployment) {
        return dsl.insertInto(DEPLOYMENTS)
                .columns(
                        DEPLOYMENTS.DEPLOYMENT_TARGET_ID,
                        DEPLOYMENTS.DEPLOYMENT_ID,
                        DEPLOYMENTS.IMAGE_VERSION,
                        DEPLOYMENTS.COMMIT_SHA,
                        DEPLOYMENTS.STATUS,
                        DEPLOYMENTS.RECORDED_AT
                )
                .values(
                        deploymentTargetId.value(),
                        deployment.deploymentId().value(),
                        deployment.imageVersion().value(),
                        commitSha(deployment.commitSha()),
                        deployment.status().name(),
                        OffsetDateTime.ofInstant(deployment.recordedAt(), ZoneOffset.UTC)
                )
                .onConflict(DEPLOYMENTS.DEPLOYMENT_TARGET_ID, DEPLOYMENTS.DEPLOYMENT_ID)
                .doNothing();
    }

    private static String commitSha(Maybe<Sha> commitSha) {
        return switch (commitSha) {
            case Maybe.Some<Sha>(var sha) -> sha.value();
            case Maybe.None<Sha>() -> null;
        };
    }
}
