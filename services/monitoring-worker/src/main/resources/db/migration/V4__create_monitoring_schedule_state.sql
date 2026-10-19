
CREATE TABLE monitoring_worker.monitoring_schedule_state (
    monitor_id BIGINT PRIMARY KEY
        CHECK (monitor_id > 0),

    check_interval_seconds INTEGER NOT NULL
        CHECK (check_interval_seconds BETWEEN 60 AND 86400),

    next_due_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_monitoring_schedule_state_due
    ON monitoring_worker.monitoring_schedule_state (next_due_at, monitor_id);
