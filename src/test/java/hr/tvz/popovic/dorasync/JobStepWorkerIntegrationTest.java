package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.adapter.in.JobStepWorkScheduler;
import hr.tvz.popovic.dorasync.adapter.in.ScheduledJobEnqueueScheduler;
import hr.tvz.popovic.dorasync.adapter.in.StaleJobReapScheduler;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepType;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.ServiceConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.ConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.ExternalReference;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.JobStep;
import hr.tvz.popovic.dorasync.application.port.in.WorkJobStepsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.DequeueJobStepsPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchConnectionPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchDeploykoDeploymentsPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchGithubHistoryPort;
import hr.tvz.popovic.dorasync.application.port.out.FetchJenkinsBuildsPort;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobPort;
import hr.tvz.popovic.dorasync.application.port.out.FinishJobStepPort;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.BuildStages.BUILD_STAGES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Builds.BUILDS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Commits.COMMITS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.DeploymentTargets.DEPLOYMENT_TARGETS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Deployments.DEPLOYMENTS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Pipelines.PIPELINES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Repositories.REPOSITORIES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.LeadTimeForChanges.LEAD_TIME_FOR_CHANGES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.TimeToRestore.TIME_TO_RESTORE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Import({TestcontainersConfiguration.class, JobStepWorkerIntegrationTest.SynchronousExecutorConfiguration.class})
@SpringBootTest
class JobStepWorkerIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class SynchronousExecutorConfiguration {

        @Bean
        @Primary
        TaskExecutorPort synchronousTaskExecutorPort() {
            return Runnable::run;
        }

        @Bean
        @Primary
        FetchGithubHistoryPort noopFetchGithubHistoryPort() {
            return (fullName, cursor) -> new FetchGithubHistoryPort.Result.Success("main", List.of());
        }

        @Bean
        @Primary
        FetchJenkinsBuildsPort noopFetchJenkinsBuildsPort() {
            return (jobPath, cursor) -> new FetchJenkinsBuildsPort.Result.Success(List.of());
        }

        @Bean
        @Primary
        FetchDeploykoDeploymentsPort noopFetchDeploykoDeploymentsPort() {
            return (service, cursor) -> new FetchDeploykoDeploymentsPort.Result.Success(List.of());
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
    private FinishJobStepPort finishJobStepPort;

    @Autowired
    private FinishJobPort finishJobPort;

    @Autowired
    private DequeueJobStepsPort dequeueJobStepsPort;

    @Autowired
    private FetchConnectionPort fetchConnectionPort;

    @BeforeEach
    void cleanUp() {
        dsl.deleteFrom(LEAD_TIME_FOR_CHANGES).execute();
        dsl.deleteFrom(COMMITS).execute();
        dsl.deleteFrom(REPOSITORIES).execute();
        dsl.deleteFrom(BUILD_STAGES).execute();
        dsl.deleteFrom(BUILDS).execute();
        dsl.deleteFrom(PIPELINES).execute();
        dsl.deleteFrom(TIME_TO_RESTORE).execute();
        dsl.deleteFrom(DEPLOYMENTS).execute();
        dsl.deleteFrom(DEPLOYMENT_TARGETS).execute();
        dsl.deleteFrom(JOB_STEPS).execute();
        dsl.deleteFrom(JOBS).execute();
        dsl.deleteFrom(SERVICE_CONNECTIONS).execute();
        dsl.deleteFrom(SERVICES).execute();
    }

    @Test
    void collectStepsSucceedingLeavesComputeMetricsStepPendingAndJobRunning() {
        var serviceId = insertService();
        var jobId = insertJob(serviceId, JobStatus.RUNNING);
        insertConnection(serviceId, ServiceConnectionType.GITHUB, "org/repo-a");
        insertConnection(serviceId, ServiceConnectionType.JENKINS, "job-x");
        insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.PENDING);
        insertStep(jobId, JobStepType.COLLECT_JENKINS, JobStepStatus.PENDING);
        insertStep(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.PENDING);

        var result = workJobStepsUseCase.work();

        // Only the two collect steps are claimed; the compute-metrics step stays gated.
        assertEquals(new WorkJobStepsUseCase.Result.Success(2), result);
        assertEquals(2, countSteps(jobId, JobStepStatus.SUCCESS));
        assertEquals(1, countStepsOfType(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.PENDING));
        assertEquals(JobStatus.RUNNING, jobStatus(jobId));
    }

    @Test
    void computeMetricsStepFinishesJobOnSecondTick() {
        var serviceId = insertService();
        var jobId = insertJob(serviceId, JobStatus.RUNNING);
        insertConnection(serviceId, ServiceConnectionType.GITHUB, "org/repo-a");
        insertConnection(serviceId, ServiceConnectionType.JENKINS, "job-x");
        insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.PENDING);
        insertStep(jobId, JobStepType.COLLECT_JENKINS, JobStepStatus.PENDING);
        insertStep(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.PENDING);

        // First tick: runs the two collect steps; the compute-metrics step is still gated.
        workJobStepsUseCase.work();
        assertEquals(JobStatus.RUNNING, jobStatus(jobId));

        // Second tick: the gate is now open, so the compute-metrics step runs and finishes the job.
        var secondResult = workJobStepsUseCase.work();

        assertEquals(new WorkJobStepsUseCase.Result.Success(1), secondResult);
        assertEquals(3, countSteps(jobId, JobStepStatus.SUCCESS));
        assertEquals(1, countStepsOfType(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.SUCCESS));
        assertEquals(JobStatus.SUCCESS, jobStatus(jobId));
    }

    @Test
    void failingCollectStepFailsJobAndItsRemainingSteps() {
        var serviceId = insertService();
        var jobId = insertJob(serviceId, JobStatus.RUNNING);
        insertConnection(serviceId, ServiceConnectionType.GITHUB, "org/repo-a");
        // No JENKINS connection: that step fails during collection.
        insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.PENDING);
        insertStep(jobId, JobStepType.COLLECT_JENKINS, JobStepStatus.PENDING);
        insertStep(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.PENDING);

        var result = workJobStepsUseCase.work();

        assertEquals(new WorkJobStepsUseCase.Result.Success(2), result);
        assertEquals(JobStatus.FAILURE, jobStatus(jobId));
        // Failing the job also fails its remaining non-terminal steps, so nothing is left dangling.
        assertEquals(0, countSteps(jobId, JobStepStatus.PENDING));
        assertEquals(0, countSteps(jobId, JobStepStatus.RUNNING));
        assertEquals(1, countStepsOfType(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.FAILURE));
    }

    @Test
    void dequeueSkipsComputeMetricsStepWhileCollectStepsAreOutstanding() {
        var jobId = insertJob(insertService(), JobStatus.RUNNING);
        insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.RUNNING);
        var computeStepId = insertStep(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.PENDING);

        var result = dequeueJobStepsPort.dequeue(10);

        var success = assertInstanceOf(DequeueJobStepsPort.Result.Success.class, result);
        assertEquals(0, success.jobSteps().size());
        assertEquals(JobStepStatus.PENDING, stepStatus(computeStepId));
    }

    @Test
    void dequeueClaimsComputeMetricsStepOnceAllCollectStepsSucceeded() {
        var jobId = insertJob(insertService(), JobStatus.RUNNING);
        insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.SUCCESS);
        var computeStepId = insertStep(jobId, JobStepType.COMPUTE_METRICS, JobStepStatus.PENDING);

        var result = dequeueJobStepsPort.dequeue(10);

        var success = assertInstanceOf(DequeueJobStepsPort.Result.Success.class, result);
        assertEquals(1, success.jobSteps().size());
        assertInstanceOf(JobStep.ComputeMetricsStep.class, success.jobSteps().getFirst());
        assertEquals(JobStepStatus.RUNNING, stepStatus(computeStepId));
    }

    @Test
    void failsStepAndJobWhenConnectionMissing() {
        var jobId = insertJob(insertService(), JobStatus.RUNNING);
        var stepId = insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.PENDING);

        var result = workJobStepsUseCase.work();

        assertEquals(new WorkJobStepsUseCase.Result.Success(1), result);
        assertEquals(JobStepStatus.FAILURE, stepStatus(stepId));
        assertEquals(JobStatus.FAILURE, jobStatus(jobId));
    }

    @Test
    void dequeueClaimsPendingStepsAsRunning() {
        var jobId = insertJob(insertService(), JobStatus.RUNNING);
        var githubStepId = insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.PENDING);

        var result = dequeueJobStepsPort.dequeue(10);

        var success = assertInstanceOf(DequeueJobStepsPort.Result.Success.class, result);
        assertEquals(1, success.jobSteps().size());
        var step = assertInstanceOf(JobStep.CollectGithubStep.class, success.jobSteps().getFirst());
        assertEquals(githubStepId, step.id());
        assertEquals(jobId, step.jobId());
        assertEquals(JobStepStatus.RUNNING, stepStatus(githubStepId));
    }

    @Test
    void fetchConnectionReturnsServiceIdAndExternalReference() {
        var serviceId = insertService();
        var jobId = insertJob(serviceId, JobStatus.RUNNING);
        insertConnection(serviceId, ServiceConnectionType.GITHUB, "org/repo-a");

        var result = fetchConnectionPort.fetch(jobId, ConnectionType.GITHUB);

        var success = assertInstanceOf(FetchConnectionPort.Result.Success.class, result);
        assertEquals(serviceId, success.serviceId());
        assertEquals(new ExternalReference("org/repo-a"), success.externalReference());
    }

    @Test
    void fetchConnectionReturnsNotFoundWhenAbsent() {
        var jobId = insertJob(insertService(), JobStatus.RUNNING);

        var result = fetchConnectionPort.fetch(jobId, ConnectionType.GITHUB);

        assertInstanceOf(FetchConnectionPort.Result.NotFound.class, result);
    }

    @Test
    void doesNotFinishJobWhileStepsRemain() {
        var jobId = insertJob(insertService(), JobStatus.RUNNING);
        var githubStepId = insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.SUCCESS);
        insertStep(jobId, JobStepType.COLLECT_JENKINS, JobStepStatus.RUNNING);

        var result = finishJobPort.succeedIfAllStepsSucceeded(jobId);

        assertInstanceOf(FinishJobPort.Result.Success.class, result);
        assertEquals(JobStatus.RUNNING, jobStatus(jobId));
    }

    @Test
    void doesNotOverwriteFailedJobWithSuccess() {
        var jobId = insertJob(insertService(), JobStatus.FAILURE);
        var stepId = insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.RUNNING);

        var stepResult = finishJobStepPort.succeed(stepId);
        var jobResult = finishJobPort.succeedIfAllStepsSucceeded(jobId);

        assertInstanceOf(FinishJobStepPort.SucceedResult.Success.class, stepResult);
        assertInstanceOf(FinishJobPort.Result.Success.class, jobResult);
        assertEquals(JobStatus.FAILURE, jobStatus(jobId));
    }

    @Test
    void skipsStepThatIsNoLongerRunning() {
        var jobId = insertJob(insertService(), JobStatus.RUNNING);
        var reapedStepId = insertStep(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.FAILURE);

        var result = finishJobStepPort.succeed(reapedStepId);

        assertInstanceOf(FinishJobStepPort.SucceedResult.NotRunning.class, result);
        assertEquals(JobStepStatus.FAILURE, stepStatus(reapedStepId));
    }

    private Id insertService() {
        var id = dsl.insertInto(SERVICES)
                .columns(SERVICES.NAME, SERVICES.NEXT_SYNC_AT)
                .values("service-" + UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC))
                .returning(SERVICES.ID)
                .fetchOne();
        return new Id(id.getId());
    }

    private Id insertJob(Id serviceId, JobStatus status) {
        var record = dsl.insertInto(JOBS)
                .columns(JOBS.SERVICE_ID, JOBS.STATUS, JOBS.LOCKED_UNTIL)
                .values(serviceId.value(), status, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .returning(JOBS.ID)
                .fetchOne();
        return new Id(record.getId());
    }

    private void insertConnection(Id serviceId, ServiceConnectionType type, String externalReference) {
        dsl.insertInto(SERVICE_CONNECTIONS)
                .columns(SERVICE_CONNECTIONS.SERVICE_ID, SERVICE_CONNECTIONS.TYPE, SERVICE_CONNECTIONS.EXTERNAL_REFERENCE)
                .values(serviceId.value(), type, externalReference)
                .execute();
    }

    private Id insertStep(Id jobId, JobStepType type, JobStepStatus status) {
        var record = dsl.insertInto(JOB_STEPS)
                .columns(JOB_STEPS.JOB_ID, JOB_STEPS.TYPE, JOB_STEPS.STATUS)
                .values(jobId.value(), type, status)
                .returning(JOB_STEPS.ID)
                .fetchOne();
        return new Id(record.getId());
    }

    private JobStatus jobStatus(Id jobId) {
        return dsl.select(JOBS.STATUS).from(JOBS).where(JOBS.ID.eq(jobId.value())).fetchOne(JOBS.STATUS);
    }

    private JobStepStatus stepStatus(Id stepId) {
        return dsl.select(JOB_STEPS.STATUS).from(JOB_STEPS).where(JOB_STEPS.ID.eq(stepId.value())).fetchOne(JOB_STEPS.STATUS);
    }

    private int countSteps(Id jobId, JobStepStatus status) {
        return dsl.fetchCount(JOB_STEPS, JOB_STEPS.JOB_ID.eq(jobId.value()).and(JOB_STEPS.STATUS.eq(status)));
    }

    private int countStepsOfType(Id jobId, JobStepType type, JobStepStatus status) {
        return dsl.fetchCount(JOB_STEPS, JOB_STEPS.JOB_ID.eq(jobId.value())
                .and(JOB_STEPS.TYPE.eq(type))
                .and(JOB_STEPS.STATUS.eq(status)));
    }
}
