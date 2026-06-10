CREATE TYPE service_connection_type AS ENUM (
    'GITHUB',
    'JENKINS',
    'DEPLOYKO'
);

CREATE TABLE service_connections
(
    id                 uuid PRIMARY KEY DEFAULT uuidv7(),
    service_id         uuid                    NOT NULL REFERENCES services (id),
    type               service_connection_type NOT NULL,
    external_reference text                    NOT NULL,
    UNIQUE (service_id, type, external_reference)
);

CREATE INDEX ix_service_connections_service_id
    ON service_connections (service_id);
