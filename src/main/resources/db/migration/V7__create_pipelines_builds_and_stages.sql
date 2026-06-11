CREATE TABLE pipelines
(
    id                    uuid PRIMARY KEY DEFAULT uuidv7(),
    service_connection_id uuid NOT NULL UNIQUE REFERENCES service_connections (id),
    full_name             text NOT NULL
);

CREATE TABLE builds
(
    id              uuid        PRIMARY KEY DEFAULT uuidv7(),
    pipeline_id     uuid        NOT NULL REFERENCES pipelines (id),
    build_number    bigint      NOT NULL,
    result          text,
    duration_millis bigint,
    commit_sha      text,
    started_at      timestamptz NOT NULL,
    UNIQUE (pipeline_id, build_number)
);

CREATE INDEX ix_builds_pipeline_build_number ON builds (pipeline_id, build_number);

CREATE TABLE build_stages
(
    id              uuid        PRIMARY KEY DEFAULT uuidv7(),
    build_id        uuid        NOT NULL REFERENCES builds (id),
    name            text        NOT NULL,
    status          text,
    duration_millis bigint,
    started_at      timestamptz,
    stage_order     integer     NOT NULL,
    UNIQUE (build_id, stage_order)
);
