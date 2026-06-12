CREATE TABLE time_to_restore
(
    failed_deployment_id   uuid    PRIMARY KEY REFERENCES deployments (id),
    restored_deployment_id uuid    NOT NULL REFERENCES deployments (id),
    deployment_target_id   uuid    NOT NULL REFERENCES deployment_targets (id),
    restored_after_seconds bigint  NOT NULL,
    -- true for the first failure of an outage (the immediately preceding terminal deployment was a
    -- success or there was none). Time to restore service averages restored_after_seconds over these.
    incident_start         boolean NOT NULL
);

CREATE INDEX ix_time_to_restore_target ON time_to_restore (deployment_target_id);
