package com.watchharbor.worker;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.watchharbor.worker.checker.MonitorCheckResult;
import com.watchharbor.worker.checker.MonitorChecker;
import com.watchharbor.worker.client.MonitorNotFoundException;
import com.watchharbor.worker.client.MonitorResponse;
import com.watchharbor.worker.client.MonitorServiceClient;
import com.watchharbor.worker.client.MonitorServiceException;

@Service
public class MonitoringJobProcessor {

    private static final Logger log
            = LoggerFactory.getLogger(MonitoringJobProcessor.class);

    private final JobClaimService jobClaimService;
    private final MonitorServiceClient monitorServiceClient;
    private final MonitorChecker monitorChecker;
    private final CheckResultPersistenceService resultPersistenceService;

    private final String workerId;
    private final Duration leaseDuration;
    private final int maxAttempts;
    private final Duration retryDelay;

    public MonitoringJobProcessor(
            JobClaimService jobClaimService,
            MonitorServiceClient monitorServiceClient,
            MonitorChecker monitorChecker,
            CheckResultPersistenceService resultPersistenceService,
            @Value("${worker.id:worker-local}") String workerId,
            @Value("${worker.lease-duration-seconds:30}") long leaseSeconds,
            @Value("${worker.max-attempts:3}") int maxAttempts,
            @Value("${worker.retry-delay-seconds:5}") long retryDelaySeconds) {

        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("Worker ID must not be blank");
        }

        if (leaseSeconds < 1 || maxAttempts < 1 || retryDelaySeconds < 1) {
            throw new IllegalArgumentException(
                    "Lease, maximum attempts, and retry delay must be positive");
        }

        this.jobClaimService = jobClaimService;
        this.monitorServiceClient = monitorServiceClient;
        this.monitorChecker = monitorChecker;
        this.resultPersistenceService = resultPersistenceService;
        this.workerId = workerId;
        this.leaseDuration = Duration.ofSeconds(leaseSeconds);
        this.maxAttempts = maxAttempts;
        this.retryDelay = Duration.ofSeconds(retryDelaySeconds);
    }

    /**
     * Processes at most one due job.
     *
     * @return true if a job was claimed; false if no due job was available.
     */
    public boolean processNextJob() {
        Optional<MonitoringJob> claimed
                = jobClaimService.claimNextJob(workerId, leaseDuration);

        if (claimed.isEmpty()) {
            return false;
        }

        MonitoringJob job = claimed.orElseThrow();

        try {
            MonitorResponse monitor
                    = monitorServiceClient.getMonitorById(job.monitorId());

            if (!monitor.enabled()) {
                boolean completed
                        = jobClaimService.completeJob(job.id(), job.claimToken());

                if (!completed) {
                    logStaleClaim(job);
                }

                return true;
            }

            MonitorCheckResult result = monitorChecker.check(monitor.url());

            resultPersistenceService.saveResultAndComplete(job, result);

            log.debug(
                    "Processed monitoring job {} with outcome {}",
                    job.id(),
                    result.outcome());

        } catch (MonitorNotFoundException exception) {
            boolean failed = jobClaimService.failJob(
                    job.id(),
                    job.claimToken(),
                    "MONITOR_NOT_FOUND");

            if (failed) {
                log.warn(
                        "Monitor {} no longer exists; job {} marked failed",
                        job.monitorId(),
                        job.id());
            } else {
                logStaleClaim(job);
            }

        } catch (MonitorServiceException exception) {
            handleMonitorServiceFailure(job);

        } catch (StaleClaimException exception) {
            // The result persistence transaction rolls back on a stale claim.
            logStaleClaim(job);
        }

        return true;
    }

    private void handleMonitorServiceFailure(MonitoringJob job) {
        if (job.attemptCount() < maxAttempts) {
            boolean retried = jobClaimService.retryJob(
                    job.id(),
                    job.claimToken(),
                    "MONITOR_SERVICE_UNAVAILABLE",
                    retryDelay);

            if (retried) {
                log.warn(
                        "Monitor Service unavailable for job {}; retry scheduled",
                        job.id());
            } else {
                logStaleClaim(job);
            }

            return;
        }

        boolean failed = jobClaimService.failJob(
                job.id(),
                job.claimToken(),
                "MONITOR_SERVICE_UNAVAILABLE");

        if (failed) {
            log.error(
                    "Monitor Service unavailable; job {} exhausted its attempts",
                    job.id());
        } else {
            logStaleClaim(job);
        }
    }

    private void logStaleClaim(MonitoringJob job) {
        log.debug(
                "Job {} claim is stale or expired; no state change was applied",
                job.id());
    }
}
