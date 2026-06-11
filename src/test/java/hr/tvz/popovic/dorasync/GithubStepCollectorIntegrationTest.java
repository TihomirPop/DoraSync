package hr.tvz.popovic.dorasync;

import hr.tvz.popovic.dorasync.adapter.in.JobStepWorkScheduler;
import hr.tvz.popovic.dorasync.adapter.in.ScheduledJobEnqueueScheduler;
import hr.tvz.popovic.dorasync.adapter.in.StaleJobReapScheduler;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepStatus;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.JobStepType;
import hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.enums.ServiceConnectionType;
import hr.tvz.popovic.dorasync.application.domain.model.Commit;
import hr.tvz.popovic.dorasync.application.domain.model.CommitCursor;
import hr.tvz.popovic.dorasync.application.domain.model.FullName;
import hr.tvz.popovic.dorasync.application.domain.model.GitIdentity;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.domain.model.Sha;
import hr.tvz.popovic.dorasync.application.port.in.WorkJobStepsUseCase;
import hr.tvz.popovic.dorasync.application.port.out.FetchGithubHistoryPort;
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

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Commits.COMMITS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.JobSteps.JOB_STEPS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Jobs.JOBS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Repositories.REPOSITORIES;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.ServiceConnections.SERVICE_CONNECTIONS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Services.SERVICES;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({TestcontainersConfiguration.class, GithubStepCollectorIntegrationTest.TestConfig.class})
@SpringBootTest
class GithubStepCollectorIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        @Primary
        TaskExecutorPort synchronousTaskExecutorPort() {
            return Runnable::run;
        }

        @Bean
        @Primary
        FakeGithubHistory fakeGithubHistory() {
            return new FakeGithubHistory();
        }
    }

    static class FakeGithubHistory implements FetchGithubHistoryPort {

        String defaultBranch = "main";
        List<Commit> commits = List.of();
        CommitCursor capturedCursor;
        int callCount;

        @Override
        public Result fetch(FullName fullName, CommitCursor cursor) {
            callCount++;
            capturedCursor = cursor;
            return new Result.Success(defaultBranch, commits);
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
    private FakeGithubHistory fakeGithubHistory;

    private final Instant t1 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(300);
    private final Instant t2 = t1.plusSeconds(100);
    private final Instant t3 = t1.plusSeconds(200);
    private final Instant t4 = t1.plusSeconds(300);

    @BeforeEach
    void cleanUp() {
        dsl.deleteFrom(COMMITS).execute();
        dsl.deleteFrom(REPOSITORIES).execute();
        dsl.deleteFrom(JOB_STEPS).execute();
        dsl.deleteFrom(JOBS).execute();
        dsl.deleteFrom(SERVICE_CONNECTIONS).execute();
        dsl.deleteFrom(SERVICES).execute();
    }

    @Test
    void firstRunBackfillsAllCommitsWithGitIdentityAndLineCounts() {
        var connectionId = setUpServiceWithGithubConnection("octo/repo");
        var jobId = insertRunningJobWithGithubStep(connectionId);

        fakeGithubHistory.commits = List.of(commit("aaa", t1), commit("bbb", t2), commit("ccc", t3));

        var result = workJobStepsUseCase.work();

        assertEquals(new WorkJobStepsUseCase.Result.Success(1), result);
        assertEquals(JobStatus.SUCCESS, jobStatus(jobId));
        assertEquals(new CommitCursor.Beginning(), fakeGithubHistory.capturedCursor, "first run has no cursor and backfills everything");

        var repository = dsl.selectFrom(REPOSITORIES).fetchOne();
        assertEquals("octo/repo", repository.getFullName());
        assertEquals("main", repository.getDefaultBranch());

        assertEquals(3, dsl.fetchCount(COMMITS));
        var bbb = dsl.selectFrom(COMMITS).where(COMMITS.SHA.eq("bbb")).fetchOne();
        assertEquals(repository.getId(), bbb.getRepositoryId());
        assertEquals(t2, bbb.getCommittedAt().toInstant());
        assertEquals("Author bbb", bbb.getAuthorName());
        assertEquals("bbb@example.com", bbb.getAuthorEmail());
        assertEquals("Committer bbb", bbb.getCommitterName());
        assertEquals(10, bbb.getAdditions());
        assertEquals(2, bbb.getDeletions());
    }

    @Test
    void secondRunUsesMaxCommittedAtCursorAndDedupesBoundaryCommit() {
        var connectionId = setUpServiceWithGithubConnection("octo/repo");

        var firstJobId = insertRunningJobWithGithubStep(connectionId);
        fakeGithubHistory.commits = List.of(commit("aaa", t1), commit("bbb", t2), commit("ccc", t3));
        workJobStepsUseCase.work();
        assertEquals(JobStatus.SUCCESS, jobStatus(firstJobId));
        assertEquals(3, dsl.fetchCount(COMMITS));

        // Second run: GitHub re-surfaces the boundary commit (ccc) plus one new commit (ddd).
        var secondJobId = insertRunningJobWithGithubStep(connectionId);
        fakeGithubHistory.callCount = 0;
        fakeGithubHistory.commits = List.of(commit("ccc", t3), commit("ddd", t4));

        workJobStepsUseCase.work();

        assertEquals(JobStatus.SUCCESS, jobStatus(secondJobId));
        assertEquals(new CommitCursor.Since(t3), fakeGithubHistory.capturedCursor, "incremental cursor is max(committed_at)");
        assertEquals(4, dsl.fetchCount(COMMITS), "boundary commit deduped, only ddd inserted");
        assertEquals(1, dsl.fetchCount(COMMITS, COMMITS.SHA.eq("ccc")));
    }

    private Commit commit(String sha, Instant committedAt) {
        return new Commit(
                new Sha(sha),
                "message " + sha,
                committedAt,
                committedAt,
                new GitIdentity("Author " + sha, sha + "@example.com"),
                new GitIdentity("Committer " + sha, sha + "@example.com"),
                10,
                2
        );
    }

    private Id setUpServiceWithGithubConnection(String externalReference) {
        var serviceId = dsl.insertInto(SERVICES)
                .columns(SERVICES.NAME, SERVICES.NEXT_SYNC_AT)
                .values("service-" + UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC))
                .returning(SERVICES.ID)
                .fetchOne()
                .getId();

        return new Id(dsl.insertInto(SERVICE_CONNECTIONS)
                .columns(SERVICE_CONNECTIONS.SERVICE_ID, SERVICE_CONNECTIONS.TYPE, SERVICE_CONNECTIONS.EXTERNAL_REFERENCE)
                .values(serviceId, ServiceConnectionType.GITHUB, externalReference)
                .returning(SERVICE_CONNECTIONS.ID)
                .fetchOne()
                .getId());
    }

    private Id insertRunningJobWithGithubStep(Id connectionId) {
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
                .values(jobId, JobStepType.COLLECT_GITHUB, JobStepStatus.PENDING)
                .execute();

        return new Id(jobId);
    }

    private JobStatus jobStatus(Id jobId) {
        return dsl.select(JOBS.STATUS).from(JOBS).where(JOBS.ID.eq(jobId.value())).fetchOne(JOBS.STATUS);
    }
}
