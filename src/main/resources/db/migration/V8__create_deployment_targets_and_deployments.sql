CREATE TABLE deployment_targets
(
    id                    uuid PRIMARY KEY DEFAULT uuidv7(),
    service_connection_id uuid NOT NULL UNIQUE REFERENCES service_connections (id),
    deployko_service      text NOT NULL
);

CREATE TABLE deployments
(
    id                   uuid        PRIMARY KEY DEFAULT uuidv7(),
    deployment_target_id uuid        NOT NULL REFERENCES deployment_targets (id),
    deployment_id        uuid        NOT NULL,
    image_version        text        NOT NULL,
    commit_sha           text,
    status               text        NOT NULL,
    recorded_at          timestamptz NOT NULL,
    UNIQUE (deployment_target_id, deployment_id)
);

CREATE INDEX ix_deployments_target_recorded_at ON deployments (deployment_target_id, recorded_at);
