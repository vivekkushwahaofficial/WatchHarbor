package com.watchharbor.worker;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
                        0,
                        null
                )
        );

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        MonitoringJob candidate = candidates.get(0);

        // Generate a unique token for this claim.
        UUID claimToken = UUID.randomUUID();

        // Claim the locked job and return its updated values.
        MonitoringJob claimedJob = jdbcTemplate.queryForObject(
                """
                UPDATE monitoring_worker.monitoring_jobs
                SET status = 'CLAIMED',
                    claimed_by = ?,
                    lease_until = CURRENT_TIMESTAMP
                        + (? * INTERVAL '1 second'),
                    attempt_count = attempt_count + 1,
                    claim_token = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING'
                RETURNING id, monitor_id, due_at, claimed_by,
                          lease_until, attempt_count, claim_token
                """,
                (rs, rowNum) -> new MonitoringJob(
                        rs.getLong("id"),
                        rs.getLong("monitor_id"),
                        rs.getObject("due_at", OffsetDateTime.class),
                        rs.getString("claimed_by"),
                        rs.getObject("lease_until", OffsetDateTime.class),
                        rs.getInt("attempt_count"),
                        rs.getObject("claim_token", UUID.class)
                ),
                workerId,
                leaseDuration.toSeconds(),
                claimToken,
                candidate.id()
        );

        return Optional.of(claimedJob);
    }

    @Transactional
    public int recoverExpiredLeases(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be at least one");
        }

        return jdbcTemplate.update(
                """
            UPDATE monitoring_worker.monitoring_jobs
            SET status = CASE
                    WHEN attempt_count >= ? THEN 'FAILED'
                    ELSE 'PENDING'
                END,
                claimed_by = NULL,
                lease_until = NULL,
                claim_token = NULL,
                last_error_code = CASE
                    WHEN attempt_count >= ?
                        THEN 'LEASE_EXPIRED_MAX_ATTEMPTS'
                    ELSE 'LEASE_EXPIRED'
                END,
                updated_at = CURRENT_TIMESTAMP
            WHERE status = 'CLAIMED'
              AND lease_until <= CURRENT_TIMESTAMP
            """,
                maxAttempts,
                maxAttempts
        );
    }

    @Transactional
    public boolean completeJob(long jobId, UUID claimToken) {
        Objects.requireNonNull(claimToken, "Claim token must not be null");

        int updatedRows = jdbcTemplate.update(
                """
            UPDATE monitoring_worker.monitoring_jobs
            SET status = 'SUCCEEDED',
                claimed_by = NULL,
                lease_until = NULL,
                claim_token = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND lease_until > CURRENT_TIMESTAMP
            """,
                jobId,
                claimToken
        );

        return updatedRows == 1;
    }

    @Transactional
    public boolean failJob(
            long jobId,
            UUID claimToken,
            String errorCode
    ) {
        Objects.requireNonNull(claimToken, "Claim token must not be null");

        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("Error code must not be blank");
        }

        if (errorCode.length() > 100) {
            throw new IllegalArgumentException(
                    "Error code must not exceed 100 characters"
            );
        }

        int updatedRows = jdbcTemplate.update(
                """
            UPDATE monitoring_worker.monitoring_jobs
            SET status = 'FAILED',
                claimed_by = NULL,
                lease_until = NULL,
                claim_token = NULL,
                last_error_code = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND lease_until > CURRENT_TIMESTAMP
            """,
                errorCode,
                jobId,
                claimToken
        );

        return updatedRows == 1;
    }

    @Transactional
    public boolean retryJob(
            long jobId,
            UUID claimToken,
            String errorCode,
            Duration retryDelay
    ) {
        Objects.requireNonNull(claimToken, "Claim token must not be null");
        Objects.requireNonNull(retryDelay, "Retry delay must not be null");

        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("Error code must not be blank");
        }

        if (errorCode.length() > 100) {
            throw new IllegalArgumentException(
                    "Error code must not exceed 100 characters");
        }

        if (retryDelay.toSeconds() < 1) {
            throw new IllegalArgumentException(
                    "Retry delay must be at least one second");
        }

        int updatedRows = jdbcTemplate.update(
                """
            UPDATE monitoring_worker.monitoring_jobs
            SET status = 'PENDING',
                due_at = CURRENT_TIMESTAMP
                    + (? * INTERVAL '1 second'),
                claimed_by = NULL,
                lease_until = NULL,
                claim_token = NULL,
                last_error_code = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND lease_until > CURRENT_TIMESTAMP
            """,
                retryDelay.toSeconds(),
                errorCode,
                jobId,
                claimToken
        );

        return updatedRows == 1;
    }

}
