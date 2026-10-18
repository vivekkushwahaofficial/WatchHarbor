package com.watchharbor.worker;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobClaimService {

    private static final int MAX_WORKER_ID_LENGTH = 200;

    private final JdbcTemplate jdbcTemplate;

    public JobClaimService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Optional<MonitoringJob> claimNextJob(
            String workerId,
            Duration leaseDuration
    ) {
        // Validate the worker identity.
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker ID must not be blank"
            );
        }

        if (workerId.length() > MAX_WORKER_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Worker ID must not exceed 200 characters"
            );
        }

        // Validate the lease duration.
        Objects.requireNonNull(
                leaseDuration,
                "Lease duration must not be null"
        );

        if (leaseDuration.toSeconds() < 1) {
            throw new IllegalArgumentException(
                    "Lease duration must be at least one second"
            );
        }

        // Lock one due job. Competing transactions skip locked rows.
        List<MonitoringJob> candidates = jdbcTemplate.query(
                """
                SELECT id, monitor_id, due_at
                FROM monitoring_worker.monitoring_jobs
                WHERE status = 'PENDING'
                  AND due_at <= CURRENT_TIMESTAMP
                ORDER BY due_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new MonitoringJob(
                        rs.getLong("id"),
                        rs.getLong("monitor_id"),
                        rs.getObject("due_at", OffsetDateTime.class),
                        null,
                        null,
                        0
                )
        );

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        MonitoringJob candidate = candidates.get(0);

        // Claim the locked job and return its updated values.
        MonitoringJob claimedJob = jdbcTemplate.queryForObject(
                """
                UPDATE monitoring_worker.monitoring_jobs
                SET status = 'CLAIMED',
                    claimed_by = ?,
                    lease_until = CURRENT_TIMESTAMP
                        + (? * INTERVAL '1 second'),
                    attempt_count = attempt_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING'
                RETURNING id, monitor_id, due_at, claimed_by,
                          lease_until, attempt_count
                """,
                (rs, rowNum) -> new MonitoringJob(
                        rs.getLong("id"),
                        rs.getLong("monitor_id"),
                        rs.getObject("due_at", OffsetDateTime.class),
                        rs.getString("claimed_by"),
                        rs.getObject("lease_until", OffsetDateTime.class),
                        rs.getInt("attempt_count")
                ),
                workerId,
                leaseDuration.toSeconds(),
                candidate.id()
        );

        return Optional.of(claimedJob);
    }
}
