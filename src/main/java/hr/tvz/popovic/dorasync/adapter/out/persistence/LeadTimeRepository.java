package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.BuildIdentity;
import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.CommittedChange;
import hr.tvz.popovic.dorasync.application.domain.model.DeliveredCommit;
import hr.tvz.popovic.dorasync.application.domain.model.DeployedVersion;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentStatus;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.ImageVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.port.out.LeadTimeRepositoryPort;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Builds.BUILDS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Commits.COMMITS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.DeploymentTargets.DEPLOYMENT_TARGETS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Deployments.DEPLOYMENTS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.LeadTimeForChanges.LEAD_TIME_FOR_CHANGES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Pipelines.PIPELINES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Repositories.REPOSITORIES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;

@Repository
public class LeadTimeRepository implements LeadTimeRepositoryPort {

    private final DSLContext dsl;

    public LeadTimeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public LoadResult loadUndeliveredCommitsAndNewDeployments(Id serviceId) {
        try {
            var undeliveredCommits = dsl
                    .select(COMMITS.ID, COMMITS.REPOSITORY_ID, COMMITS.SHA, COMMITS.COMMITTED_AT)
                    .from(COMMITS)
                    .join(REPOSITORIES).on(COMMITS.REPOSITORY_ID.eq(REPOSITORIES.ID))
                    .join(SERVICE_CONNECTIONS).on(REPOSITORIES.SERVICE_CONNECTION_ID.eq(SERVICE_CONNECTIONS.ID))
                    .where(SERVICE_CONNECTIONS.SERVICE_ID.eq(serviceId.value()))
                    .andNotExists(dsl.selectOne()
                            .from(LEAD_TIME_FOR_CHANGES)
                            .where(LEAD_TIME_FOR_CHANGES.COMMIT_ID.eq(COMMITS.ID)))
                    .fetch(record -> new CommittedChange(
                            new Id(record.get(COMMITS.ID)),
                            new Id(record.get(COMMITS.REPOSITORY_ID)),
                            new Sha(record.get(COMMITS.SHA)),
                            record.get(COMMITS.COMMITTED_AT).toInstant()
                    ));

            var newDeployments = dsl
                    .select(DEPLOYMENTS.ID, DEPLOYMENTS.IMAGE_VERSION, DEPLOYMENTS.COMMIT_SHA, DEPLOYMENTS.RECORDED_AT)
                    .from(DEPLOYMENTS)
                    .join(DEPLOYMENT_TARGETS).on(DEPLOYMENTS.DEPLOYMENT_TARGET_ID.eq(DEPLOYMENT_TARGETS.ID))
                    .join(SERVICE_CONNECTIONS).on(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID.eq(SERVICE_CONNECTIONS.ID))
                    .where(SERVICE_CONNECTIONS.SERVICE_ID.eq(serviceId.value()))
                    .and(DEPLOYMENTS.STATUS.eq(DeploymentStatus.SUCCESS.name()))
                    .and(newerThanFrontier(serviceId))
                    .fetch(record -> new DeployedVersion(
                            new Id(record.get(DEPLOYMENTS.ID)),
                            new ImageVersion(record.get(DEPLOYMENTS.IMAGE_VERSION)),
                            maybeSha(record.get(DEPLOYMENTS.COMMIT_SHA)),
                            record.get(DEPLOYMENTS.RECORDED_AT).toInstant()
                    ));

            return new LoadResult.Success(undeliveredCommits, newDeployments);

        } catch (DataAccessException e) {
            return new LoadResult.Failure(e);
        }
    }

    @Override
    public LoadBuildsResult loadBuilds(Id serviceId, Set<BuildNumber> buildNumbers) {
        if (buildNumbers.isEmpty()) {
            return new LoadBuildsResult.Success(List.of());
        }

        try {
            var numbers = buildNumbers.stream().map(BuildNumber::value).toList();

            var builds = dsl
                    .select(BUILDS.BUILD_NUMBER, BUILDS.COMMIT_SHA)
                    .from(BUILDS)
                    .join(PIPELINES).on(BUILDS.PIPELINE_ID.eq(PIPELINES.ID))
                    .join(SERVICE_CONNECTIONS).on(PIPELINES.SERVICE_CONNECTION_ID.eq(SERVICE_CONNECTIONS.ID))
                    .where(SERVICE_CONNECTIONS.SERVICE_ID.eq(serviceId.value()))
                    .and(BUILDS.BUILD_NUMBER.in(numbers))
                    .fetch(record -> new BuildIdentity(
                            new BuildNumber(record.get(BUILDS.BUILD_NUMBER)),
                            maybeSha(record.get(BUILDS.COMMIT_SHA))
                    ));

            return new LoadBuildsResult.Success(builds);

        } catch (DataAccessException e) {
            return new LoadBuildsResult.Failure(e);
        }
    }

    @Override
    public SaveResult save(List<DeliveredCommit> deliveredCommits) {
        if (deliveredCommits.isEmpty()) {
            return new SaveResult.Success(0);
        }

        try {
            List<Query> inserts = deliveredCommits.stream()
                    .map(this::insert)
                    .toList();

            int[] inserted = dsl.batch(inserts).execute();

            return new SaveResult.Success(Arrays.stream(inserted).sum());

        } catch (DataAccessException e) {
            return new SaveResult.Failure(e);
        }
    }

    private Query insert(DeliveredCommit deliveredCommit) {
        return dsl.insertInto(LEAD_TIME_FOR_CHANGES)
                .columns(
                        LEAD_TIME_FOR_CHANGES.COMMIT_ID,
                        LEAD_TIME_FOR_CHANGES.DEPLOYMENT_ID,
                        LEAD_TIME_FOR_CHANGES.REPOSITORY_ID,
                        LEAD_TIME_FOR_CHANGES.LEAD_TIME_SECONDS
                )
                .values(
                        deliveredCommit.commitId().value(),
                        deliveredCommit.deploymentId().value(),
                        deliveredCommit.repositoryId().value(),
                        deliveredCommit.leadTime().toSeconds()
                )
                .onConflict(LEAD_TIME_FOR_CHANGES.COMMIT_ID)
                .doNothing();
    }

    /**
     * Restricts deployments to those past the delivery frontier — the newest {@code recorded_at}
     * already referenced in {@code lead_time_for_changes} for this service. {@code true} on the first
     * run (frontier is {@code NULL}), so the full history is loaded once.
     */
    private Condition newerThanFrontier(Id serviceId) {
        OffsetDateTime frontier = dsl
                .select(DSL.max(DEPLOYMENTS.RECORDED_AT))
                .from(LEAD_TIME_FOR_CHANGES)
                .join(DEPLOYMENTS).on(LEAD_TIME_FOR_CHANGES.DEPLOYMENT_ID.eq(DEPLOYMENTS.ID))
                .join(DEPLOYMENT_TARGETS).on(DEPLOYMENTS.DEPLOYMENT_TARGET_ID.eq(DEPLOYMENT_TARGETS.ID))
                .join(SERVICE_CONNECTIONS).on(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID.eq(SERVICE_CONNECTIONS.ID))
                .where(SERVICE_CONNECTIONS.SERVICE_ID.eq(serviceId.value()))
                .fetchOne(0, OffsetDateTime.class);

        return frontier == null ? DSL.trueCondition() : DEPLOYMENTS.RECORDED_AT.gt(frontier);
    }

    private static Maybe<Sha> maybeSha(String value) {
        return value == null ? new Maybe.None<>() : new Maybe.Some<>(new Sha(value));
    }
}
