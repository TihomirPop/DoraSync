-- Read-only flattening views for Grafana dashboards. Each view resolves the multi-hop join back to
-- services (via service_connections) so a dashboard query is a flat
--   SELECT ... WHERE service_name IN ($service) AND $__timeFilter(<ts>) ...
-- No code reads these; they exist purely so dashboard SQL stays readable. Additive and non-breaking.

CREATE VIEW v_deployments AS
SELECT d.id,
       d.deployment_target_id,
       d.deployment_id,
       d.image_version,
       d.commit_sha,
       d.status,
       d.recorded_at,
       sc.service_id,
       s.name AS service_name
FROM deployments d
         JOIN deployment_targets dt ON dt.id = d.deployment_target_id
         JOIN service_connections sc ON sc.id = dt.service_connection_id
         JOIN services s ON s.id = sc.service_id;

CREATE VIEW v_commits AS
SELECT c.id,
       c.repository_id,
       c.sha,
       c.message,
       c.authored_at,
       c.committed_at,
       c.author_name,
       c.author_email,
       c.committer_name,
       c.committer_email,
       c.additions,
       c.deletions,
       (COALESCE(c.additions, 0) + COALESCE(c.deletions, 0)) AS churn,
       sc.service_id,
       s.name                                                AS service_name
FROM commits c
         JOIN repositories r ON r.id = c.repository_id
         JOIN service_connections sc ON sc.id = r.service_connection_id
         JOIN services s ON s.id = sc.service_id;

CREATE VIEW v_builds AS
SELECT b.id,
       b.pipeline_id,
       b.build_number,
       b.result,
       b.duration_millis,
       b.commit_sha,
       b.started_at,
       sc.service_id,
       s.name AS service_name
FROM builds b
         JOIN pipelines p ON p.id = b.pipeline_id
         JOIN service_connections sc ON sc.id = p.service_connection_id
         JOIN services s ON s.id = sc.service_id;

CREATE VIEW v_build_stages AS
SELECT bs.id,
       bs.build_id,
       bs.name,
       bs.status,
       bs.duration_millis,
       bs.started_at,
       bs.stage_order,
       b.build_number,
       b.started_at AS build_started_at,
       sc.service_id,
       s.name       AS service_name
FROM build_stages bs
         JOIN builds b ON b.id = bs.build_id
         JOIN pipelines p ON p.id = b.pipeline_id
         JOIN service_connections sc ON sc.id = p.service_connection_id
         JOIN services s ON s.id = sc.service_id;

-- Time axis is the delivering deployment's recorded_at: when the change actually reached production.
CREATE VIEW v_lead_time AS
SELECT lt.commit_id,
       lt.deployment_id,
       lt.repository_id,
       lt.lead_time_seconds,
       d.recorded_at  AS delivered_at,
       c.committed_at,
       sc.service_id,
       s.name         AS service_name
FROM lead_time_for_changes lt
         JOIN deployments d ON d.id = lt.deployment_id
         JOIN commits c ON c.id = lt.commit_id
         JOIN repositories r ON r.id = lt.repository_id
         JOIN service_connections sc ON sc.id = r.service_connection_id
         JOIN services s ON s.id = sc.service_id;

-- Time axis is the failed deployment's recorded_at: when the outage began.
CREATE VIEW v_time_to_restore AS
SELECT ttr.failed_deployment_id,
       ttr.restored_deployment_id,
       ttr.deployment_target_id,
       ttr.restored_after_seconds,
       ttr.incident_start,
       fd.recorded_at AS failed_at,
       rd.recorded_at AS restored_at,
       sc.service_id,
       s.name         AS service_name
FROM time_to_restore ttr
         JOIN deployments fd ON fd.id = ttr.failed_deployment_id
         JOIN deployments rd ON rd.id = ttr.restored_deployment_id
         JOIN deployment_targets dt ON dt.id = ttr.deployment_target_id
         JOIN service_connections sc ON sc.id = dt.service_connection_id
         JOIN services s ON s.id = sc.service_id;
