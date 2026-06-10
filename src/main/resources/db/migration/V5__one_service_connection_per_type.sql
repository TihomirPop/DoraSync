ALTER TABLE service_connections
    DROP CONSTRAINT service_connections_service_id_type_external_reference_key;

ALTER TABLE service_connections
    ADD CONSTRAINT service_connections_service_id_type_key UNIQUE (service_id, type);
