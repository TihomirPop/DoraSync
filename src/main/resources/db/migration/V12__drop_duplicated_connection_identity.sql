-- The upstream identifier already lives on service_connections.external_reference.
-- These columns were write-only copies of it (refreshed every collection, never read),
-- so the connection is now the single source of truth for a service's identity.
ALTER TABLE repositories
    DROP COLUMN full_name;

ALTER TABLE pipelines
    DROP COLUMN full_name;

ALTER TABLE deployment_targets
    DROP COLUMN deployko_service;
