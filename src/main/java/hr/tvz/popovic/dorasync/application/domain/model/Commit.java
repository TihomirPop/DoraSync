package hr.tvz.popovic.dorasync.application.domain.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record Commit(
        Sha sha,
        String message,
        Instant authoredAt,
        Instant committedAt,
        Maybe<GitIdentity> author,
        Maybe<GitIdentity> committer,
        int additions,
        int deletions
) {

    public Commit {
        requireNonNull(sha, "sha must not be null");
        requireNonNull(message, "message must not be null");
        requireNonNull(authoredAt, "authoredAt must not be null");
        requireNonNull(committedAt, "committedAt must not be null");
        requireNonNull(author, "author must not be null");
        requireNonNull(committer, "committer must not be null");
    }
}
