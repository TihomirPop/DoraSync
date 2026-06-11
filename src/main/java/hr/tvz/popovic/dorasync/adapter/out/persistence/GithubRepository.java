package hr.tvz.popovic.dorasync.adapter.out.persistence;

import hr.tvz.popovic.dorasync.application.domain.model.Commit;
import hr.tvz.popovic.dorasync.application.domain.model.CommitCursor;
import hr.tvz.popovic.dorasync.application.domain.model.FullName;
import hr.tvz.popovic.dorasync.application.domain.model.GitIdentity;
import hr.tvz.popovic.dorasync.application.domain.model.Id;
import hr.tvz.popovic.dorasync.application.port.out.GithubRepositoryPort;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Commits.COMMITS;
import static hr.tvz.popovic.dorasync.adapter.out.persistence.jooq.generated.tables.Repositories.REPOSITORIES;
import static org.jooq.impl.DSL.max;

@Repository
public class GithubRepository implements GithubRepositoryPort {

    private final DSLContext dsl;

    public GithubRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public FindCursorResult findLatestCommittedAt(Id serviceConnectionId) {
        try {
            var latest = dsl.select(max(COMMITS.COMMITTED_AT))
                    .from(COMMITS)
                    .join(REPOSITORIES).on(COMMITS.REPOSITORY_ID.eq(REPOSITORIES.ID))
                    .where(REPOSITORIES.SERVICE_CONNECTION_ID.eq(serviceConnectionId.value()))
                    .fetchOne(max(COMMITS.COMMITTED_AT));

            CommitCursor cursor = latest == null
                    ? new CommitCursor.Beginning()
                    : new CommitCursor.Since(latest.toInstant());

            return new FindCursorResult.Success(cursor);

        } catch (DataAccessException e) {
            return new FindCursorResult.Failure(e);
        }
    }

    @Override
    public UpsertRepositoryResult upsertRepository(Id serviceConnectionId, FullName fullName, String defaultBranch) {
        try {
            var record = dsl.insertInto(REPOSITORIES)
                    .columns(REPOSITORIES.SERVICE_CONNECTION_ID, REPOSITORIES.FULL_NAME, REPOSITORIES.DEFAULT_BRANCH)
                    .values(serviceConnectionId.value(), fullName.value(), defaultBranch)
                    .onConflict(REPOSITORIES.SERVICE_CONNECTION_ID)
                    .doUpdate()
                    .set(REPOSITORIES.FULL_NAME, fullName.value())
                    .set(REPOSITORIES.DEFAULT_BRANCH, defaultBranch)
                    .returning(REPOSITORIES.ID)
                    .fetchOne();

            if (record == null) {
                return new UpsertRepositoryResult.Failure(new IllegalStateException("Upsert returned no record"));
            }

            return new UpsertRepositoryResult.Success(new Id(record.getId()));

        } catch (DataAccessException e) {
            return new UpsertRepositoryResult.Failure(e);
        }
    }

    @Override
    public SaveCommitsResult saveCommits(Id repositoryId, List<Commit> commits) {
        if (commits.isEmpty()) {
            return new SaveCommitsResult.Success(0);
        }

        try {
            List<Query> inserts = commits.stream()
                    .map(commit -> insert(repositoryId, commit))
                    .toList();

            int[] inserted = dsl.batch(inserts).execute();

            return new SaveCommitsResult.Success(Arrays.stream(inserted).sum());

        } catch (DataAccessException e) {
            return new SaveCommitsResult.Failure(e);
        }
    }

    private Query insert(Id repositoryId, Commit commit) {
        var author = identityOrEmpty(commit.author());
        var committer = identityOrEmpty(commit.committer());

        return dsl.insertInto(COMMITS)
                .columns(
                        COMMITS.REPOSITORY_ID,
                        COMMITS.SHA,
                        COMMITS.MESSAGE,
                        COMMITS.AUTHORED_AT,
                        COMMITS.COMMITTED_AT,
                        COMMITS.AUTHOR_NAME,
                        COMMITS.AUTHOR_EMAIL,
                        COMMITS.COMMITTER_NAME,
                        COMMITS.COMMITTER_EMAIL,
                        COMMITS.ADDITIONS,
                        COMMITS.DELETIONS
                )
                .values(
                        repositoryId.value(),
                        commit.sha().value(),
                        commit.message(),
                        OffsetDateTime.ofInstant(commit.authoredAt(), ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(commit.committedAt(), ZoneOffset.UTC),
                        author.name(),
                        author.email(),
                        committer.name(),
                        committer.email(),
                        commit.additions(),
                        commit.deletions()
                )
                .onConflict(COMMITS.REPOSITORY_ID, COMMITS.SHA)
                .doNothing();
    }

    private static GitIdentity identityOrEmpty(GitIdentity identity) {
        return identity == null ? new GitIdentity(null, null) : identity;
    }
}
