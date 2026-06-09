CREATE TYPE job_status AS ENUM (
    'RUNNING',
    'SUCCESS',
    'FAILURE'
);

CREATE TYPE job_step_type AS ENUM (
    'COLLECT_GITHUB',
    'COLLECT_JENKINS',
    'COLLECT_DEPLOYKO'
);

CREATE TYPE job_step_status AS ENUM (
    'PENDING',
    'RUNNING',
    'SUCCESS',
    'FAILURE'
);

CREATE TABLE services
(
    id           uuid PRIMARY KEY DEFAULT uuidv7(),
    name         text        NOT NULL,
    next_sync_at timestamptz NOT NULL
);

CREATE TABLE jobs
(
    id           uuid PRIMARY KEY DEFAULT uuidv7(),
    service_id   uuid       NOT NULL REFERENCES services (id),
    status       job_status NOT NULL,
    locked_until timestamptz
);

CREATE UNIQUE INDEX ux_jobs_one_running_per_service
    ON jobs (service_id) WHERE status = 'RUNNING'::job_status;

CREATE INDEX ix_jobs_service_id
    ON jobs (service_id);

CREATE TABLE job_steps
(
    id           uuid PRIMARY KEY DEFAULT uuidv7(),
    job_id       uuid            NOT NULL REFERENCES jobs (id),
    type         job_step_type   NOT NULL,
    status       job_step_status NOT NULL,
    locked_until timestamptz
);

CREATE UNIQUE INDEX ux_job_steps_one_running_per_job_and_type
    ON job_steps (job_id, type) WHERE status = 'RUNNING'::job_step_status;

CREATE INDEX ix_job_steps_job_id
    ON job_steps (job_id);