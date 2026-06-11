package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.adapter.in.JobStepWorkScheduler;
import hr.tvz.popovic.dorasync.adapter.in.ScheduledJobEnqueueScheduler;
import hr.tvz.popovic.dorasync.adapter.in.StaleJobReapScheduler;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepType;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.ServiceConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Deployment;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentCursor;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentId;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentStatus;
import hr.tvz.popovic.dorasync.application.domain.model.DeploykoService;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.ImageVersion;
import hr.tvz.popovic.dorasync.application.domain.model.Maybe;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.port.in.WorkJobStepsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.FetchDeploykoDeploymentsPort;
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

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.DeploymentTargets.DEPLOYMENT_TARGETS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Deployments.DEPLOYMENTS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Import({TestcontainersConfiguration.class, DeploykoStepCollectorIntegrationTest.TestConfig.class})
@SpringBootTest
class DeploykoStepCollectorIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        @Primary
        TaskExecutorPort synchronousTaskExecutorPort() {
            return Runnable::run;
        }

        @Bean
        @Primary
        FakeDeploykoDeployments fakeDeploykoDeployments() {
            return new FakeDeploykoDeployments();
        }
    }

    static class FakeDeploykoDeployments implements FetchDeploykoDeploymentsPort {

        List<Deployment> deployments = List.of();
        DeploymentCursor capturedCursor;
        int callCount;

        @Override
        public Result fetch(DeploykoService service, DeploymentCursor cursor) {
            callCount++;
            capturedCursor = cursor;
            return new Result.Success(deployments);
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
    private FakeDeploykoDeployments fakeDeploykoDeployments;

    private final Instant t1 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(300);
    private final Instant t2 = t1.plusSeconds(100);
    private final Instant t3 = t1.plusSeconds(200);
    private final Instant t4 = t1.plusSeconds(300);

    @BeforeEach
    void cleanUp() {
        dsl.deleteFrom(DEPLOYMENTS).execute();
        dsl.deleteFrom(DEPLOYMENT_TARGETS).execute();
        dsl.deleteFrom(JOB_STEPS).execute();
        dsl.deleteFrom(JOBS).execute();
        dsl.deleteFrom(SERVICE_CONNECTIONS).execute();
        dsl.deleteFrom(SERVICES).execute();
    }

    @Test
    void firstRunBackfillsTerminalDeploymentsAndSkipsInProgress() {
        var connectionId = setUpServiceWithDeploykoConnection("deployko-api");
        var jobId = insertRunningJobWithDeploykoStep(connectionId);

        var success = deployment("1.0.0", "f5a1c2d", DeploymentStatus.SUCCESS, t1);
        var failure = deployment("1.0.1", null, DeploymentStatus.FAILURE, t2);
        var inProgress = deployment("1.0.2", null, DeploymentStatus.IN_PROGRESS, t3);
        fakeDeploykoDeployments.deployments = List.of(success, failure, inProgress);

        var result = workJobStepsUseCase.work();

        assertEquals(new WorkJobStepsUseCase.Result.Success(1), result);
        assertEquals(JobStatus.SUCCESS, jobStatus(jobId));
        assertEquals(new DeploymentCursor.Beginning(), fakeDeploykoDeployments.capturedCursor, "first run has no cursor and backfills everything");

        var target = dsl.selectFrom(DEPLOYMENT_TARGETS).fetchOne();
        assertEquals("deployko-api", target.getDeploykoService());

        assertEquals(2, dsl.fetchCount(DEPLOYMENTS), "in-progress deployment skipped");

        var successRow = dsl.selectFrom(DEPLOYMENTS).where(DEPLOYMENTS.IMAGE_VERSION.eq("1.0.0")).fetchOne();
        assertEquals(target.getId(), successRow.getDeploymentTargetId());
        assertEquals(success.deploymentId().value(), successRow.getDeploymentId());
        assertEquals("SUCCESS", successRow.getStatus());
        assertEquals("f5a1c2d", successRow.getCommitSha());
        assertEquals(t1, successRow.getRecordedAt().toInstant());

        var failureRow = dsl.selectFrom(DEPLOYMENTS).where(DEPLOYMENTS.IMAGE_VERSION.eq("1.0.1")).fetchOne();
        assertEquals("FAILURE", failureRow.getStatus());
        assertNull(failureRow.getCommitSha(), "null commit sha tolerated");
    }

    @Test
    void secondRunUsesMaxRecordedAtCursorAndDedupesBoundaryDeployment() {
        var connectionId = setUpServiceWithDeploykoConnection("deployko-api");

        // The boundary deployment (recorded at t2) keeps its stable Deployko id across polls.
        var boundary = deployment("1.0.1", "bbb", DeploymentStatus.SUCCESS, t2);

        var firstJobId = insertRunningJobWithDeploykoStep(connectionId);
        fakeDeploykoDeployments.deployments = List.of(
                deployment("1.0.0", "aaa", DeploymentStatus.SUCCESS, t1),
                boundary
        );
        workJobStepsUseCase.work();
        assertEquals(JobStatus.SUCCESS, jobStatus(firstJobId));
        assertEquals(2, dsl.fetchCount(DEPLOYMENTS));

        // Second run: the inclusive `since` re-surfaces the boundary deployment (t2) plus a new one (t4).
        var secondJobId = insertRunningJobWithDeploykoStep(connectionId);
        fakeDeploykoDeployments.callCount = 0;
        fakeDeploykoDeployments.deployments = List.of(
                boundary,
                deployment("1.0.2", "ccc", DeploymentStatus.SUCCESS, t4)
        );

        workJobStepsUseCase.work();

        assertEquals(JobStatus.SUCCESS, jobStatus(secondJobId));
        assertEquals(new DeploymentCursor.Since(t2), fakeDeploykoDeployments.capturedCursor, "incremental cursor is max(recorded_at)");
        assertEquals(3, dsl.fetchCount(DEPLOYMENTS), "boundary deployment deduped, only the new one inserted");
        assertEquals(1, dsl.fetchCount(DEPLOYMENTS, DEPLOYMENTS.IMAGE_VERSION.eq("1.0.1")));
    }

    private Deployment deployment(String imageVersion, String commitSha, DeploymentStatus status, Instant recordedAt) {
        return new Deployment(
                new DeploymentId(UUID.randomUUID()),
                new ImageVersion(imageVersion),
                Maybe.of(commitSha == null ? null : new Sha(commitSha)),
                status,
                recordedAt
        );
    }

    private Id setUpServiceWithDeploykoConnection(String externalReference) {
        var serviceId = dsl.insertInto(SERVICES)
                .columns(SERVICES.NAME, SERVICES.NEXT_SYNC_AT)
                .values("service-" + UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC))
                .returning(SERVICES.ID)
                .fetchOne()
                .getId();

        return new Id(dsl.insertInto(SERVICE_CONNECTIONS)
                .columns(SERVICE_CONNECTIONS.SERVICE_ID, SERVICE_CONNECTIONS.TYPE, SERVICE_CONNECTIONS.EXTERNAL_REFERENCE)
                .values(serviceId, ServiceConnectionType.DEPLOYKO, externalReference)
                .returning(SERVICE_CONNECTIONS.ID)
                .fetchOne()
                .getId());
    }

    private Id insertRunningJobWithDeploykoStep(Id connectionId) {
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
                .values(jobId, JobStepType.COLLECT_DEPLOYKO, JobStepStatus.PENDING)
                .execute();

        return new Id(jobId);
    }

    private JobStatus jobStatus(Id jobId) {
        return dsl.select(JOBS.STATUS).from(JOBS).where(JOBS.ID.eq(jobId.value())).fetchOne(JOBS.STATUS);
    }
}
