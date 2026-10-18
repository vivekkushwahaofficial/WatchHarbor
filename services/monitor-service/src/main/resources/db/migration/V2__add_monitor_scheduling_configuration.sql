ALTER TABLE monitors
    ADD COLUMN check_interval_seconds INTEGER NOT NULL DEFAULT 300
        CHECK (check_interval_seconds BETWEEN 60 AND 86400),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;