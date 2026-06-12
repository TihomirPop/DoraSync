package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.adapter.in.JobStepWorkScheduler;
import hr.tvz.popovic.dorasync.adapter.in.ScheduledJobEnqueueScheduler;
import hr.tvz.popovic.dorasync.adapter.in.StaleJobReapScheduler;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepType;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.ServiceConnectionType;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.records.LeadTimeForChangesRecord;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.records.TimeToRestoreRecord;
import hr.tvz.popovic.dorasync.application.domain.model.DeploymentStatus;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.in.WorkJobStepsUseCase;
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
import java.util.UUID;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.BuildStages.BUILD_STAGES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Builds.BUILDS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Commits.COMMITS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.DeploymentTargets.DEPLOYMENT_TARGETS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Deployments.DEPLOYMENTS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.LeadTimeForChanges.LEAD_TIME_FOR_CHANGES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Pipelines.PIPELINES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Repositories.REPOSITORIES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.TimeToRestore.TIME_TO_RESTORE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({TestcontainersConfiguration.class, ComputeMetricsStepProcessorIntegrationTest.SynchronousExecutorConfiguration.class})
@SpringBootTest
class ComputeMetricsStepProcessorIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class SynchronousExecutorConfiguration {

        @Bean
        @Primary
        TaskExecutorPort synchronousTaskExecutorPort() {
            return Runnable::run;
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

    private final Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(1000);

    @BeforeEach
    void cleanUp() {
        dsl.deleteFrom(LEAD_TIME_FOR_CHANGES).execute();
        dsl.deleteFrom(TIME_TO_RESTORE).execute();
        dsl.deleteFrom(DEPLOYMENTS).execute();
        dsl.deleteFrom(DEPLOYMENT_TARGETS).execute();
        dsl.deleteFrom(COMMITS).execute();
        dsl.deleteFrom(REPOSITORIES).execute();
        dsl.deleteFrom(BUILD_STAGES).execute();
        dsl.deleteFrom(BUILDS).execute();
        dsl.deleteFrom(PIPELINES).execute();
        dsl.deleteFrom(JOB_STEPS).execute();
        dsl.deleteFrom(JOBS).execute();
        dsl.deleteFrom(SERVICE_CONNECTIONS).execute();
        dsl.deleteFrom(SERVICES).execute();
    }

    @Test
    void recordsEveryFailureOfAStreakFlaggingOnlyTheFirstAsIncidentStartAndFinishesJob() {
        var target = insertServiceWithDeploymentTarget();
        insertDeployment(target, DeploymentStatus.SUCCESS, t0);
        var firstFailure = insertDeployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100));
        var secondFailure = insertDeployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(200));
        var recovery = insertDeployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(400));
        var jobId = insertRunningJobWithComputeMetricsStep(target);

        var result = workJobStepsUseCase.work();

        assertEquals(new WorkJobStepsUseCase.Result.Success(1), result);
        assertEquals(JobStatus.SUCCESS, jobStatus(jobId));

        assertEquals(2, dsl.fetchCount(TIME_TO_RESTORE), "both failures of the streak are recorded");

        var first = restoredFailure(firstFailure);
        assertTrue(first.getIncidentStart(), "first failure after a success starts the outage");
        assertEquals(recovery.value(), first.getRestoredDeploymentId());
        assertEquals(target.value(), first.getDeploymentTargetId());
        assertEquals(300L, first.getRestoredAfterSeconds(), "first failure (t+100) to recovery (t+400)");

        var second = restoredFailure(secondFailure);
        assertFalse(second.getIncidentStart(), "a failure following another failure is not an incident start");
        assertEquals(recovery.value(), second.getRestoredDeploymentId(), "shares the success that ends the outage");
        assertEquals(200L, second.getRestoredAfterSeconds());
    }

    @Test
    void canceledDeploymentBetweenFailureAndSuccessDoesNotResetTheIncident() {
        var target = insertServiceWithDeploymentTarget();
        insertDeployment(target, DeploymentStatus.SUCCESS, t0);
        var failure = insertDeployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100));
        insertDeployment(target, DeploymentStatus.CANCELED, t0.plusSeconds(200));
        var recovery = insertDeployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(300));
        insertRunningJobWithComputeMetricsStep(target);

        workJobStepsUseCase.work();

        assertEquals(1, dsl.fetchCount(TIME_TO_RESTORE));
        var restored = restoredFailure(failure);
        assertTrue(restored.getIncidentStart());
        assertEquals(recovery.value(), restored.getRestoredDeploymentId(), "CANCELED is ignored; recovery is the later SUCCESS");
    }

    @Test
    void rerunIsIdempotentAndResolvesAPreviouslyUnresolvedFailure() {
        var target = insertServiceWithDeploymentTarget();
        insertDeployment(target, DeploymentStatus.SUCCESS, t0);
        var failure = insertDeployment(target, DeploymentStatus.FAILURE, t0.plusSeconds(100));

        // First run: the failure has no recovery yet, so nothing is paired.
        insertRunningJobWithComputeMetricsStep(target);
        workJobStepsUseCase.work();
        assertEquals(0, dsl.fetchCount(TIME_TO_RESTORE), "unresolved failure is deferred");

        // A recovery arrives; a second metrics run pairs it without duplicating anything.
        var recovery = insertDeployment(target, DeploymentStatus.SUCCESS, t0.plusSeconds(250));
        insertRunningJobWithComputeMetricsStep(target);
        workJobStepsUseCase.work();

        assertEquals(1, dsl.fetchCount(TIME_TO_RESTORE));
        var restored = restoredFailure(failure);
        assertEquals(recovery.value(), restored.getRestoredDeploymentId());
        assertTrue(restored.getIncidentStart());

        // A third run over the same data inserts nothing new (the failure is now recorded and skipped).
        insertRunningJobWithComputeMetricsStep(target);
        workJobStepsUseCase.work();
        assertEquals(1, dsl.fetchCount(TIME_TO_RESTORE), "re-run does not duplicate the recorded failure");
    }

    @Test
    void linksCommitsToTheirEarliestDeploymentByShaAndBuildNumberFallback() {
        var service = insertServiceWithAllConnections();
        var c1 = insertCommit(service.repositoryId(), "aaaaaaa1", t0);
        var c2 = insertCommit(service.repositoryId(), "bbbbbbb2", t0.plusSeconds(100));
        var c3 = insertCommit(service.repositoryId(), "ccccccc3", t0.plusSeconds(200));
        insertBuild(service.pipelineId(), 42, "bbbbbbb2", t0.plusSeconds(90));
        // d1 carries its commit SHA directly; d2 has none and is resolved via build 42 in its image version.
        var d1 = insertDeployment(service.deploymentTargetId(), DeploymentStatus.SUCCESS, t0.plusSeconds(50), "image-aaaaaaa1", "aaaaaaa1");
        var d2 = insertDeployment(service.deploymentTargetId(), DeploymentStatus.SUCCESS, t0.plusSeconds(150), "42-bbbbbbb", null);
        insertRunningJobWithComputeMetricsStep(service.deploymentTargetId());

        workJobStepsUseCase.work();

        assertEquals(2, dsl.fetchCount(LEAD_TIME_FOR_CHANGES), "c1 and c2 are delivered; c3 has no deployment yet");

        var first = deliveredCommit(c1);
        assertEquals(d1.value(), first.getDeploymentId(), "linked by SHA");
        assertEquals(service.repositoryId().value(), first.getRepositoryId());
        assertEquals(50L, first.getLeadTimeSeconds(), "commit (t0) to deploy (t+50)");

        var second = deliveredCommit(c2);
        assertEquals(d2.value(), second.getDeploymentId(), "linked via build 42 from image version 42-bbbbbbb");
        assertEquals(50L, second.getLeadTimeSeconds(), "commit (t+100) to deploy (t+150)");

        assertNull(deliveredCommit(c3), "no deployment ships c3 or a newer commit yet");
    }

    @Test
    void incrementalRerunDeliversNewerCommitsWithoutTouchingAlreadyDeliveredOnes() {
        var service = insertServiceWithAllConnections();
        var c1 = insertCommit(service.repositoryId(), "aaaaaaa1", t0);
        var c2 = insertCommit(service.repositoryId(), "bbbbbbb2", t0.plusSeconds(100));
        // One deployment ships the newer commit; it delivers both commits up to it.
        var d1 = insertDeployment(service.deploymentTargetId(), DeploymentStatus.SUCCESS, t0.plusSeconds(150), "image-bbbbbbb2", "bbbbbbb2");
        insertRunningJobWithComputeMetricsStep(service.deploymentTargetId());
        workJobStepsUseCase.work();

        assertEquals(2, dsl.fetchCount(LEAD_TIME_FOR_CHANGES), "c1 and c2 both delivered by the first deployment");
        assertEquals(d1.value(), deliveredCommit(c1).getDeploymentId());
        assertEquals(150L, deliveredCommit(c1).getLeadTimeSeconds(), "c1 (t0) to deploy (t+150)");

        // A newer commit and a newer deployment arrive; only the new tail is loaded next run.
        var c3 = insertCommit(service.repositoryId(), "ccccccc3", t0.plusSeconds(200));
        var d2 = insertDeployment(service.deploymentTargetId(), DeploymentStatus.SUCCESS, t0.plusSeconds(400), "image-ccccccc3", "ccccccc3");
        insertRunningJobWithComputeMetricsStep(service.deploymentTargetId());
        workJobStepsUseCase.work();

        assertEquals(3, dsl.fetchCount(LEAD_TIME_FOR_CHANGES), "only c3 is added");
        assertEquals(d1.value(), deliveredCommit(c1).getDeploymentId(), "an already-delivered commit keeps its original deployment");
        assertEquals(d2.value(), deliveredCommit(c3).getDeploymentId());
        assertEquals(200L, deliveredCommit(c3).getLeadTimeSeconds(), "c3 (t+200) to deploy (t+400)");
    }

    private Id insertServiceWithDeploymentTarget() {
        var serviceId = dsl.insertInto(SERVICES)
                .columns(SERVICES.NAME, SERVICES.NEXT_SYNC_AT)
                .values("service-" + UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC))
                .returning(SERVICES.ID)
                .fetchOne()
                .getId();

        var connectionId = dsl.insertInto(SERVICE_CONNECTIONS)
                .columns(SERVICE_CONNECTIONS.SERVICE_ID, SERVICE_CONNECTIONS.TYPE, SERVICE_CONNECTIONS.EXTERNAL_REFERENCE)
                .values(serviceId, ServiceConnectionType.DEPLOYKO, "deployko-api")
                .returning(SERVICE_CONNECTIONS.ID)
                .fetchOne()
                .getId();

        return new Id(dsl.insertInto(DEPLOYMENT_TARGETS)
                .columns(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID)
                .values(connectionId)
                .returning(DEPLOYMENT_TARGETS.ID)
                .fetchOne()
                .getId());
    }

    private Id insertDeployment(Id deploymentTargetId, DeploymentStatus status, Instant recordedAt) {
        return insertDeployment(deploymentTargetId, status, recordedAt, "1.0.0", null);
    }

    private Id insertDeployment(Id deploymentTargetId, DeploymentStatus status, Instant recordedAt, String imageVersion, String commitSha) {
        return new Id(dsl.insertInto(DEPLOYMENTS)
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
                        UUID.randomUUID(),
                        imageVersion,
                        commitSha,
                        status.name(),
                        OffsetDateTime.ofInstant(recordedAt, ZoneOffset.UTC)
                )
                .returning(DEPLOYMENTS.ID)
                .fetchOne()
                .getId());
    }

    private TestService insertServiceWithAllConnections() {
        var deploymentTargetId = insertServiceWithDeploymentTarget();
        var serviceId = serviceIdOf(deploymentTargetId);
        return new TestService(serviceId, deploymentTargetId, insertRepository(serviceId), insertPipeline(serviceId));
    }

    private Id insertRepository(Id serviceId) {
        var connectionId = insertConnection(serviceId, ServiceConnectionType.GITHUB, "owner/repo");
        return new Id(dsl.insertInto(REPOSITORIES)
                .columns(REPOSITORIES.SERVICE_CONNECTION_ID)
                .values(connectionId)
                .returning(REPOSITORIES.ID)
                .fetchOne()
                .getId());
    }

    private Id insertPipeline(Id serviceId) {
        var connectionId = insertConnection(serviceId, ServiceConnectionType.JENKINS, "owner/pipeline");
        return new Id(dsl.insertInto(PIPELINES)
                .columns(PIPELINES.SERVICE_CONNECTION_ID)
                .values(connectionId)
                .returning(PIPELINES.ID)
                .fetchOne()
                .getId());
    }

    private UUID insertConnection(Id serviceId, ServiceConnectionType type, String externalReference) {
        return dsl.insertInto(SERVICE_CONNECTIONS)
                .columns(SERVICE_CONNECTIONS.SERVICE_ID, SERVICE_CONNECTIONS.TYPE, SERVICE_CONNECTIONS.EXTERNAL_REFERENCE)
                .values(serviceId.value(), type, externalReference)
                .returning(SERVICE_CONNECTIONS.ID)
                .fetchOne()
                .getId();
    }

    private Id insertCommit(Id repositoryId, String sha, Instant committedAt) {
        var at = OffsetDateTime.ofInstant(committedAt, ZoneOffset.UTC);
        return new Id(dsl.insertInto(COMMITS)
                .columns(COMMITS.REPOSITORY_ID, COMMITS.SHA, COMMITS.AUTHORED_AT, COMMITS.COMMITTED_AT)
                .values(repositoryId.value(), sha, at, at)
                .returning(COMMITS.ID)
                .fetchOne()
                .getId());
    }

    private void insertBuild(Id pipelineId, long buildNumber, String commitSha, Instant startedAt) {
        dsl.insertInto(BUILDS)
                .columns(BUILDS.PIPELINE_ID, BUILDS.BUILD_NUMBER, BUILDS.COMMIT_SHA, BUILDS.STARTED_AT)
                .values(pipelineId.value(), buildNumber, commitSha, OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC))
                .execute();
    }

    private Id serviceIdOf(Id deploymentTargetId) {
        return new Id(dsl.select(SERVICE_CONNECTIONS.SERVICE_ID)
                .from(DEPLOYMENT_TARGETS)
                .join(SERVICE_CONNECTIONS).on(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID.eq(SERVICE_CONNECTIONS.ID))
                .where(DEPLOYMENT_TARGETS.ID.eq(deploymentTargetId.value()))
                .fetchOne(SERVICE_CONNECTIONS.SERVICE_ID));
    }

    private LeadTimeForChangesRecord deliveredCommit(Id commitId) {
        return dsl.selectFrom(LEAD_TIME_FOR_CHANGES)
                .where(LEAD_TIME_FOR_CHANGES.COMMIT_ID.eq(commitId.value()))
                .fetchOne();
    }

    private record TestService(Id serviceId, Id deploymentTargetId, Id repositoryId, Id pipelineId) {
    }

    private Id insertRunningJobWithComputeMetricsStep(Id deploymentTargetId) {
        var serviceId = dsl.select(SERVICE_CONNECTIONS.SERVICE_ID)
                .from(DEPLOYMENT_TARGETS)
                .join(SERVICE_CONNECTIONS).on(DEPLOYMENT_TARGETS.SERVICE_CONNECTION_ID.eq(SERVICE_CONNECTIONS.ID))
                .where(DEPLOYMENT_TARGETS.ID.eq(deploymentTargetId.value()))
                .fetchOne(SERVICE_CONNECTIONS.SERVICE_ID);

        var jobId = dsl.insertInto(JOBS)
                .columns(JOBS.SERVICE_ID, JOBS.STATUS, JOBS.LOCKED_UNTIL)
                .values(serviceId, JobStatus.RUNNING, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .returning(JOBS.ID)
                .fetchOne()
                .getId();

        dsl.insertInto(JOB_STEPS)
                .columns(JOB_STEPS.JOB_ID, JOB_STEPS.TYPE, JOB_STEPS.STATUS)
                .values(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.PENDING)
                .execute();

        return new Id(jobId);
    }

    private JobStatus jobStatus(Id jobId) {
        return dsl.select(JOBS.STATUS).from(JOBS).where(JOBS.ID.eq(jobId.value())).fetchOne(JOBS.STATUS);
    }

    private TimeToRestoreRecord restoredFailure(Id failedDeploymentId) {
        return dsl.selectFrom(TIME_TO_RESTORE)
                .where(TIME_TO_RESTORE.FAILED_DEPLOYMENT_ID.eq(failedDeploymentId.value()))
                .fetchOne();
    }
}
