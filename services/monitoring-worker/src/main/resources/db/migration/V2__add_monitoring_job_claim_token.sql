ALTER TABLE monitoring_worker.monitoring_jobs
    ADD COLUMN claim_token UUID;

ALTER TABLE monitoring_worker.monitoring_jobs
    ADD CONSTRAINT ck_monitoring_jobs_claim_token_consistency
    CHECK (
        (status = 'CLAIMED' AND claim_token IS NOT NULL)
        OR
        (status <> 'CLAIMED' AND claim_token IS NULL)
    );