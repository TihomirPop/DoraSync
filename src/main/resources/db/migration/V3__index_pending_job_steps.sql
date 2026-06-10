CREATE INDEX ix_job_steps_pending
    ON job_steps (id) WHERE status = 'PENDING'::job_step_status;
