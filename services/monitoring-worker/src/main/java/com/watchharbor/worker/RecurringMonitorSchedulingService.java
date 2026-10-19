package com.watchharbor.worker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringMonitorSchedulingService {

    private final JdbcTemplate jdbcTemplate;

    public RecurringMonitorSchedulingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a job if the monitor is enabled, due, and has no active job.
     *
     * @return true when a new job was created.
     */
    @Transactional
    public boolean scheduleIfDue(
            long monitorId,
            int intervalSeconds,
            boolean enabled
    ) {
        if (monitorId <= 0) {
            throw new IllegalArgumentException(
                    "Monitor ID must be positive"
            );
        }

        if (intervalSeconds < 60 || intervalSeconds > 86400) {
            throw new IllegalArgumentException(
                    "Interval must be between 60 and 86400 seconds"
            );
        }

        if (!enabled) {
            return false;
        }

        // Create the schedule on first discovery. Concurrent calls are safe:
        // the monitor_id primary key permits only one schedule row.
        jdbcTemplate.update(
                """
                INSERT INTO monitoring_worker.monitoring_schedule_state
                    (monitor_id, check_interval_seconds, next_due_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (monitor_id) DO NOTHING
                """,
                monitorId,
                intervalSeconds
        );

        // Serialize scheduling attempts for this monitor.
        var state = jdbcTemplate.queryForMap(
                """
                SELECT check_interval_seconds, next_due_at
                FROM monitoring_worker.monitoring_schedule_state
                WHERE monitor_id = ?
                FOR UPDATE
                """,
                monitorId
        );

        int storedInterval = ((Number) state
                .get("check_interval_seconds")).intValue();

        // Apply changed intervals immediately. The next check becomes due now.
        if (storedInterval != intervalSeconds) {
            jdbcTemplate.update(
                    """
                    UPDATE monitoring_worker.monitoring_schedule_state
                    SET check_interval_seconds = ?,
                        next_due_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE monitor_id = ?
                    """,
                    intervalSeconds,
                    monitorId
            );
        }

        Integer due = jdbcTemplate.queryForObject(
                """
                SELECT CASE
                    WHEN next_due_at <= CURRENT_TIMESTAMP THEN 1
                    ELSE 0
                END
                FROM monitoring_worker.monitoring_schedule_state
                WHERE monitor_id = ?
                """,
                Integer.class,
                monitorId
        );

        if (due == null || due == 0) {
            return false;
        }

        // Do not create a second pending or claimed job for this monitor.
        Integer activeJobs = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM monitoring_worker.monitoring_jobs
                WHERE monitor_id = ?
                  AND status IN ('PENDING', 'CLAIMED')
                """,
                Integer.class,
                monitorId
        );

        if (activeJobs != null && activeJobs > 0) {
            // Keep the schedule due. Reconciliation will try again later.
            return false;
        }

        // The existing partial unique index is the final duplicate safeguard.
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO monitoring_worker.monitoring_jobs
                    (monitor_id, status, due_at)
                VALUES (?, 'PENDING', CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """,
                monitorId
        );

        if (inserted != 1) {
            return false;
        }

        // Advance only after a job was successfully inserted.
        jdbcTemplate.update(
                """
                UPDATE monitoring_worker.monitoring_schedule_state
                SET next_due_at = CURRENT_TIMESTAMP
                    + (? * INTERVAL '1 second'),
                    updated_at = CURRENT_TIMESTAMP
                WHERE monitor_id = ?
                """,
                intervalSeconds,
                monitorId
        );

        return true;
    }
}
