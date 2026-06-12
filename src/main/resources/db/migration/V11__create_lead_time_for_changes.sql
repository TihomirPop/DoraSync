CREATE TABLE lead_time_for_changes
(
    -- one row per delivered commit: the earliest deployment that shipped this commit (or a newer one)
    commit_id         uuid   PRIMARY KEY REFERENCES commits (id),
    deployment_id     uuid   NOT NULL REFERENCES deployments (id),
    repository_id     uuid   NOT NULL REFERENCES repositories (id),
    -- recorded_at of the delivering deployment minus committed_at of the commit. Lead time for
    -- changes averages lead_time_seconds over these rows.
    lead_time_seconds bigint NOT NULL
);

CREATE INDEX ix_lead_time_for_changes_repository ON lead_time_for_changes (repository_id);
