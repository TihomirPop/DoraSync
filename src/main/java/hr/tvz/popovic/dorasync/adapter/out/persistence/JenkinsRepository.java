package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.Build;
import hr.tvz.popovic.dorasync.application.domain.model.BuildCursor;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.domain.model.Stage;
import hr.tvz.popovic.dorasync.application.port.out.JenkinsRepositoryPort;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.BuildStages.BUILD_STAGES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Builds.BUILDS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Pipelines.PIPELINES;
import static org.jooq.impl.DSL.max;

@Repository
public class JenkinsRepository implements JenkinsRepositoryPort {

    private final DSLContext dsl;

    public JenkinsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public FindCursorResult findLatestBuildNumber(Id serviceConnectionId) {
        try {
            var latest = dsl.select(max(BUILDS.BUILD_NUMBER))
                    .from(BUILDS)
                    .join(PIPELINES).on(BUILDS.PIPELINE_ID.eq(PIPELINES.ID))
                    .where(PIPELINES.SERVICE_CONNECTION_ID.eq(serviceConnectionId.value()))
                    .fetchOne(max(BUILDS.BUILD_NUMBER));

            BuildCursor cursor = latest == null
                    ? new BuildCursor.Beginning()
                    : new BuildCursor.After(latest);

            return new FindCursorResult.Success(cursor);

        } catch (DataAccessException e) {
            return new FindCursorResult.Failure(e);
        }
    }

    @Override
    public UpsertPipelineResult upsertPipeline(Id serviceConnectionId) {
        try {
            // Find-or-create: the no-op self-update lets RETURNING fire on the existing row too,
            // since service_connection_id is now the only column on the table.
            var record = dsl.insertInto(PIPELINES)
                    .columns(PIPELINES.SERVICE_CONNECTION_ID)
                    .values(serviceConnectionId.value())
                    .onConflict(PIPELINES.SERVICE_CONNECTION_ID)
                    .doUpdate()
                    .set(PIPELINES.SERVICE_CONNECTION_ID, serviceConnectionId.value())
                    .returning(PIPELINES.ID)
                    .fetchOne();

            if (record == null) {
                return new UpsertPipelineResult.Failure(new IllegalStateException("Upsert returned no record"));
            }

            return new UpsertPipelineResult.Success(new Id(record.getId()));

        } catch (DataAccessException e) {
            return new UpsertPipelineResult.Failure(e);
        }
    }

    @Override
    public SaveBuildsResult saveBuilds(Id pipelineId, List<Build> builds) {
        if (builds.isEmpty()) {
            return new SaveBuildsResult.Success(0);
        }

        try {
            int saved = 0;
            for (Build build : builds) {
                var commitSha = switch (build.commitSha()) {
                    case Maybe.Some<Sha>(var sha) -> sha.value();
                    case Maybe.None<Sha>() -> null;
                };

                var record = dsl.insertInto(BUILDS)
                        .columns(
                                BUILDS.PIPELINE_ID,
                                BUILDS.BUILD_NUMBER,
                                BUILDS.RESULT,
                                BUILDS.DURATION_MILLIS,
                                BUILDS.COMMIT_SHA,
                                BUILDS.STARTED_AT
                        )
                        .values(
                                pipelineId.value(),
                                build.buildNumber().value(),
                                build.result().name(),
                                build.durationMillis(),
                                commitSha,
                                OffsetDateTime.ofInstant(build.startedAt(), ZoneOffset.UTC)
                        )
                        .onConflict(BUILDS.PIPELINE_ID, BUILDS.BUILD_NUMBER)
                        .doNothing()
                        .returning(BUILDS.ID)
                        .fetchOne();

                if (record == null) {
                    continue;
                }

                saved++;
                saveStages(new Id(record.getId()), build.stages());
            }

            return new SaveBuildsResult.Success(saved);

        } catch (DataAccessException e) {
            return new SaveBuildsResult.Failure(e);
        }
    }

    private void saveStages(Id buildId, List<Stage> stages) {
        if (stages.isEmpty()) {
            return;
        }

        List<Query> inserts = stages.stream()
                .map(stage -> {
                    var startedAt = switch (stage.startedAt()) {
                        case Maybe.Some<Instant>(var instant) -> OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
                        case Maybe.None<Instant>() -> null;
                    };
                    return (Query) dsl.insertInto(BUILD_STAGES)
                            .columns(
                                    BUILD_STAGES.BUILD_ID,
                                    BUILD_STAGES.NAME,
                                    BUILD_STAGES.STATUS,
                                    BUILD_STAGES.DURATION_MILLIS,
                                    BUILD_STAGES.STARTED_AT,
                                    BUILD_STAGES.STAGE_ORDER
                            )
                            .values(
                                    buildId.value(),
                                    stage.name(),
                                    stage.status().name(),
                                    stage.durationMillis(),
                                    startedAt,
                                    stage.order()
                            );
                })
                .toList();

        dsl.batch(inserts).execute();
    }
}
