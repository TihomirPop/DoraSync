package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.adapter.in.JobStepWorkScheduler;
import hr.tvz.popovic.dorasync.adapter.in.ScheduledJobEnqueueScheduler;
import hr.tvz.popovic.dorasync.adapter.in.StaleJobReapScheduler;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepType;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.ServiceConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Build;
import hr.tvz.popovic.dorasync.application.domain.model.BuildCursor;
import hr.tvz.popovic.dorasync.application.domain.model.BuildNumber;
import hr.tvz.popovic.dorasync.application.domain.model.BuildResult;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobPath;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.domain.model.Stage;
import hr.tvz.popovic.dorasync.application.domain.model.StageStatus;
import hr.tvz.popovic.dorasync.application.port.in.WorkJobStepsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.FetchJenkinsBuildsPort;
import hr.tvz.popovic.dorasync.application.port.out.TaskExecutorPort;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.BuildStages.BUILD_STAGES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Builds.BUILDS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Pipelines.PIPELINES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({TestcontainersConfiguration.class, JenkinsStepCollectorIntegrationTest.TestConfig.class})
@SpringBootTest
class JenkinsStepCollectorIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        @Primary
        TaskExecutorPort synchronousTaskExecutorPort() {
            return Runnable::run;
        }

        @Bean
        @Primary
        FakeJenkinsBuilds fakeJenkinsBuilds() {
            return new FakeJenkinsBuilds();
        }
    }

    static class FakeJenkinsBuilds implements FetchJenkinsBuildsPort {

        List<Build> builds = List.of();
        BuildCursor capturedCursor;
        int callCount;

        @Override
        public Result fetch(JobPath jobPath, BuildCursor cursor) {
            callCount++;
            capturedCursor = cursor;
            return new Result.Success(builds);
        }
    }

    @MockitoBean
    private JobStepWorkScheduler jobStepWorkScheduler;

    @MockitoBean
    private ScheduledJobEnqueueScheduler scheduledJobEnqueueScheduler;

    @MockitoBean
    private StaleJobReapScheduler staleJobReapScheduler;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private WorkJobStepsUseCase workJobStepsUseCase;

    @Autowired
    private FakeJenkinsBuilds fakeJenkinsBuilds;

    private final Instant t1 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(300);
    private final Instant t2 = t1.plusSeconds(100);
    private final Instant t3 = t1.plusSeconds(200);
    private final Instant t4 = t1.plusSeconds(300);

    @BeforeEach
    void cleanUp() {
        dsl.deleteFrom(BUILD_STAGES).execute();
        dsl.deleteFrom(BUILDS).execute();
        dsl.deleteFrom(PIPELINES).execute();
        dsl.deleteFrom(JOB_STEPS).execute();
        dsl.deleteFrom(JOBS).execute();
        dsl.deleteFrom(SERVICE_CONNECTIONS).execute();
        dsl.deleteFrom(SERVICES).execute();
    }

    @Test
    void firstRunBackfillsAllBuildsWithStages() {
        var connectionId = setUpServiceWithJenkinsConnection("DoraSync");
        var jobId = insertRunningJobWithJenkinsStep(connectionId);

        fakeJenkinsBuilds.builds = List.of(
                build(1, BuildResult.SUCCESS, "aaa", t1, stage("Build", StageStatus.SUCCESS, 1000, 0), stage("Test", StageStatus.SUCCESS, 2000, 1)),
                build(2, BuildResult.FAILURE, "bbb", t2, stage("Build", StageStatus.SUCCESS, 1500, 0), stage("Test", StageStatus.FAILED, 500, 1)),
                build(3, BuildResult.SUCCESS, "ccc", t3)
        );

        var result = workJobStepsUseCase.work();

        assertEquals(new WorkJobStepsUseCase.Result.Success(1), result);
        assertEquals(JobStatus.SUCCESS, jobStatus(jobId));
        assertEquals(new BuildCursor.Beginning(), fakeJenkinsBuilds.capturedCursor, "first run has no cursor and backfills everything");

        var pipeline = dsl.selectFrom(PIPELINES).fetchOne();
        assertEquals("DoraSync", pipeline.getFullName());

        assertEquals(3, dsl.fetchCount(BUILDS));
        var second = dsl.selectFrom(BUILDS).where(BUILDS.BUILD_NUMBER.eq(2L)).fetchOne();
        assertEquals(pipeline.getId(), second.getPipelineId());
        assertEquals("FAILURE", second.getResult());
        assertEquals(1500L + 500L, durationOfBuild(2));
        assertEquals("bbb", second.getCommitSha());
        assertEquals(t2, second.getStartedAt().toInstant());

        // Stages of build #2 persisted with order, status, duration.
        var stages = dsl.selectFrom(BUILD_STAGES)
                .where(BUILD_STAGES.BUILD_ID.eq(second.getId()))
                .orderBy(BUILD_STAGES.STAGE_ORDER.asc())
                .fetch();
        assertEquals(2, stages.size());
        assertEquals("Build", stages.get(0).getName());
        assertEquals("SUCCESS", stages.get(0).getStatus());
        assertEquals(1500L, stages.get(0).getDurationMillis());
        assertEquals("Test", stages.get(1).getName());
        assertEquals("FAILED", stages.get(1).getStatus());

        // Build #3 has no stages.
        var third = dsl.selectFrom(BUILDS).where(BUILDS.BUILD_NUMBER.eq(3L)).fetchOne();
        assertEquals(0, dsl.fetchCount(BUILD_STAGES, BUILD_STAGES.BUILD_ID.eq(third.getId())));
    }

    @Test
    void secondRunUsesMaxBuildNumberCursorAndDedupesExistingBuild() {
        var connectionId = setUpServiceWithJenkinsConnection("DoraSync");

        var firstJobId = insertRunningJobWithJenkinsStep(connectionId);
        fakeJenkinsBuilds.builds = List.of(
                build(1, BuildResult.SUCCESS, "aaa", t1),
                build(2, BuildResult.SUCCESS, "bbb", t2),
                build(3, BuildResult.SUCCESS, "ccc", t3)
        );
        workJobStepsUseCase.work();
        assertEquals(JobStatus.SUCCESS, jobStatus(firstJobId));
        assertEquals(3, dsl.fetchCount(BUILDS));

        // Second run: Jenkins re-surfaces an existing build (#3) plus one new build (#4).
        var secondJobId = insertRunningJobWithJenkinsStep(connectionId);
        fakeJenkinsBuilds.callCount = 0;
        fakeJenkinsBuilds.builds = List.of(
                build(3, BuildResult.SUCCESS, "ccc", t3),
                build(4, BuildResult.SUCCESS, "ddd", t4, stage("Build", StageStatus.SUCCESS, 1000, 0))
        );

        workJobStepsUseCase.work();

        assertEquals(JobStatus.SUCCESS, jobStatus(secondJobId));
        assertEquals(new BuildCursor.After(3), fakeJenkinsBuilds.capturedCursor, "incremental cursor is max(build_number)");
        assertEquals(4, dsl.fetchCount(BUILDS), "existing build deduped, only #4 inserted");
        assertEquals(1, dsl.fetchCount(BUILDS, BUILDS.BUILD_NUMBER.eq(4L)));

        var fourth = dsl.selectFrom(BUILDS).where(BUILDS.BUILD_NUMBER.eq(4L)).fetchOne();
        assertEquals(1, dsl.fetchCount(BUILD_STAGES, BUILD_STAGES.BUILD_ID.eq(fourth.getId())));
    }

    private long durationOfBuild(long number) {
        return dsl.select(BUILDS.DURATION_MILLIS).from(BUILDS).where(BUILDS.BUILD_NUMBER.eq(number)).fetchOne(BUILDS.DURATION_MILLIS);
    }

    private Build build(long number, BuildResult result, String sha, Instant startedAt, Stage... stages) {
        long duration = 0;
        for (Stage stage : stages) {
            duration += stage.durationMillis();
        }
        return new Build(
                new BuildNumber(number),
                result,
                duration,
                new Maybe.Some<>(new Sha(sha)),
                startedAt,
                List.of(stages)
        );
    }

    private Stage stage(String name, StageStatus status, long durationMillis, int order) {
        return new Stage(name, status, durationMillis, new Maybe.None<>(), order);
    }

    private Id setUpServiceWithJenkinsConnection(String externalReference) {
        var serviceId = dsl.insertInto(SERVICES)
                .columns(SERVICES.NAME, SERVICES.NEXT_SYNC_AT)
                .values("service-" + UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC))
                .returning(SERVICES.ID)
                .fetchOne()
                .getId();

        return new Id(dsl.insertInto(SERVICE_CONNECTIONS)
                .columns(SERVICE_CONNECTIONS.SERVICE_ID, SERVICE_CONNECTIONS.TYPE, SERVICE_CONNECTIONS.EXTERNAL_REFERENCE)
                .values(serviceId, ServiceConnectionType.JENKINS, externalReference)
                .returning(SERVICE_CONNECTIONS.ID)
                .fetchOne()
                .getId());
    }

    private Id insertRunningJobWithJenkinsStep(Id connectionId) {
        var serviceId = dsl.select(SERVICE_CONNECTIONS.SERVICE_ID)
                .from(SERVICE_CONNECTIONS)
                .where(SERVICE_CONNECTIONS.ID.eq(connectionId.value()))
                .fetchOne(SERVICE_CONNECTIONS.SERVICE_ID);

        var jobId = dsl.insertInto(JOBS)
                .columns(JOBS.SERVICE_ID, JOBS.STATUS, JOBS.LOCKED_UNTIL)
                .values(serviceId, JobStatus.RUNNING, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .returning(JOBS.ID)
                .fetchOne()
                .getId();

        dsl.insertInto(JOB_STEPS)
                .columns(JOB_STEPS.JOB_ID, JOB_STEPS.TYPE, JOB_STEPS.STATUS)
                .values(jobId, JobStepType.COLLECT_JENKINS, JobStepStatus.PENDING)
                .execute();

        return new Id(jobId);
    }

    private JobStatus jobStatus(Id jobId) {
        return dsl.select(JOBS.STATUS).from(JOBS).where(JOBS.ID.eq(jobId.value())).fetchOne(JOBS.STATUS);
    }
}
