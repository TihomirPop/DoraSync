CREATE TABLE repositories
(
    id                    uuid PRIMARY KEY DEFAULT uuidv7(),
    service_connection_id uuid NOT NULL UNIQUE REFERENCES service_connections (id),
    full_name             text NOT NULL,
    default_branch        text
);

CREATE TABLE commits
(
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    repository_id   uuid        NOT NULL REFERENCES repositories (id),
    sha             text        NOT NULL,
    message         text,
    authored_at     timestamptz NOT NULL,
    committed_at    timestamptz NOT NULL,
    author_name     text,
    author_email    text,
    committer_name  text,
    committer_email text,
    additions       integer,
    deletions       integer,
    UNIQUE (repository_id, sha)
);

-- Drives the incremental cursor (max committed_at) and future lead-time queries.
CREATE INDEX ix_commits_repository_committed_at ON commits (repository_id, committed_at);
