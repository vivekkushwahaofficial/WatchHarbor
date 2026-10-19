package com.watchharbor.worker;

import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.watchharbor.worker.checker.MonitorCheckResult;

@Service
public class CheckResultPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final JobClaimService jobClaimService;

    public CheckResultPersistenceService(
            JdbcTemplate jdbcTemplate,
            JobClaimService jobClaimService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jobClaimService = jobClaimService;
    }

    @Transactional
    public void saveResultAndComplete(
            MonitoringJob job,
            MonitorCheckResult result
    ) {
        Objects.requireNonNull(job, "Job must not be null");
        Objects.requireNonNull(result, "Result must not be null");

        jdbcTemplate.update(
                """
                INSERT INTO monitoring_worker.monitoring_check_results (
                    job_id,
                    monitor_id,
                    attempt_count,
                    outcome,
                    http_status,
                    response_time_ms,
                    error_code
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                job.id(),
                job.monitorId(),
                job.attemptCount(),
                result.outcome().name(),
                result.httpStatus(),
                result.responseTime().toMillis(),
                result.errorCode()
        );

        boolean completed = jobClaimService.completeJob(
                job.id(),
                job.claimToken()
        );

        if (!completed) {
            // Throwing causes the result INSERT to roll back.
            throw new StaleClaimException();
        }
    }
}
