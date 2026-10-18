package com.watchharbor.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class MonitoringWorkerApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobClaimService jobClaimService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final AtomicLong MONITOR_IDS
            = new AtomicLong(System.currentTimeMillis() * 1_000L);

    private final List<Long> createdMonitorIds = new ArrayList<>();

    @AfterEach
    public void cleanUpTestJobs() {
        for (Long monitorId : createdMonitorIds) {
            jdbcTemplate.update(
                    """
                DELETE FROM monitoring_worker.monitoring_jobs
                WHERE monitor_id = ?
                """,
                    monitorId
            );
        }

        createdMonitorIds.clear();
    }

    @Test
    void flywayCreatesMonitoringJobsTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """,
                Integer.class,
                "monitoring_worker",
                "monitoring_jobs"
        );

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    void rejectsBlankWorkerId() {
        assertThatThrownBy(()
                -> jobClaimService.claimNextJob(
                        " ",
                        Duration.ofSeconds(30)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Worker ID must not be blank");
    }

    @Test
    void rejectsWorkerIdLongerThan200Characters() {
        String workerId = "w".repeat(201);

        assertThatThrownBy(()
                -> jobClaimService.claimNextJob(
                        workerId,
                        Duration.ofSeconds(30)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Worker ID must not exceed 200 characters");
    }

    @Test
    void rejectsLeaseDurationShorterThanOneSecond() {
        assertThatThrownBy(()
                -> jobClaimService.claimNextJob(
                        "worker-1",
                        Duration.ofMillis(500)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lease duration must be at least one second");
    }

    @Test
    void rejectsZeroLeaseDuration() {
        assertThatThrownBy(()
                -> jobClaimService.claimNextJob(
                        "worker-1",
                        Duration.ZERO
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lease duration must be at least one second");
    }

    @Test
    void rejectsNegativeLeaseDuration() {
        assertThatThrownBy(()
                -> jobClaimService.claimNextJob(
                        "worker-1",
                        Duration.ofSeconds(-1)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lease duration must be at least one second");
    }

    @Test
    void claimsDuePendingJob() {
        long monitorId = insertJob(-60);

        Optional<MonitoringJob> result
                = jobClaimService.claimNextJob(
                        "worker-1",
                        Duration.ofMinutes(2)
                );

        assertThat(result).isPresent();

        MonitoringJob job = result.orElseThrow();

        assertThat(job.monitorId()).isEqualTo(monitorId);
        assertThat(job.claimedBy()).isEqualTo("worker-1");
        assertThat(job.leaseUntil()).isNotNull();
        assertThat(job.attemptCount()).isEqualTo(1);

        String status = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM monitoring_worker.monitoring_jobs
                WHERE id = ?
                """,
                String.class,
                job.id()
        );

        assertThat(status).isEqualTo("CLAIMED");
    }

    @Test
    void doesNotClaimFutureDueJob() {
        insertJob(3600);

        Optional<MonitoringJob> result
                = jobClaimService.claimNextJob(
                        "worker-1",
                        Duration.ofMinutes(2)
                );

        assertThat(result).isEmpty();
    }

    @Test
    void recoversExpiredLeaseAndClearsClaimDetails() {
        insertJob(-60);

        MonitoringJob claimedJob = jobClaimService.claimNextJob(
                "worker-1",
                Duration.ofMinutes(2)
        ).orElseThrow();

        // Simulate a worker whose lease has expired.
        jdbcTemplate.update(
                """
                UPDATE monitoring_worker.monitoring_jobs
                SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """,
                claimedJob.id()
        );

        int recoveredCount = jobClaimService.recoverExpiredLeases();

        assertThat(recoveredCount).isEqualTo(1);

        var row = jdbcTemplate.queryForMap(
                """
                SELECT status, claimed_by, lease_until, attempt_count
                FROM monitoring_worker.monitoring_jobs
                WHERE id = ?
                """,
                claimedJob.id()
        );

        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("claimed_by")).isNull();
        assertThat(row.get("lease_until")).isNull();
        assertThat(((Number) row.get("attempt_count")).intValue())
                .isEqualTo(1);
    }

    @Test
    void doesNotRecoverUnexpiredLease() {
        insertJob(-60);

        MonitoringJob claimedJob = jobClaimService.claimNextJob(
                "worker-1",
                Duration.ofMinutes(2)
        ).orElseThrow();

        int recoveredCount = jobClaimService.recoverExpiredLeases();

        assertThat(recoveredCount).isZero();

        String status = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM monitoring_worker.monitoring_jobs
                WHERE id = ?
                """,
                String.class,
                claimedJob.id()
        );

        assertThat(status).isEqualTo("CLAIMED");
    }

    @Test
    void anotherWorkerCanClaimRecoveredJob() {
        long monitorId = insertJob(-60);

        MonitoringJob firstClaim = jobClaimService.claimNextJob(
                "worker-1",
                Duration.ofMinutes(2)
        ).orElseThrow();

        jdbcTemplate.update(
                """
                UPDATE monitoring_worker.monitoring_jobs
                SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """,
                firstClaim.id()
        );

        assertThat(jobClaimService.recoverExpiredLeases()).isEqualTo(1);

        MonitoringJob secondClaim = jobClaimService.claimNextJob(
                "worker-2",
                Duration.ofMinutes(2)
        ).orElseThrow();

        assertThat(secondClaim.monitorId()).isEqualTo(monitorId);
        assertThat(secondClaim.claimedBy()).isEqualTo("worker-2");
        assertThat(secondClaim.attemptCount()).isEqualTo(2);
    }

    @Test
    void competingWorkersCannotClaimTheSameJob()
            throws Exception {

        long monitorId = insertJob(-60);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<MonitoringJob>> first = executor.submit(() -> {
                ready.countDown();
                start.await();

                return jobClaimService.claimNextJob(
                        "worker-1",
                        Duration.ofMinutes(2)
                );
            });

            Future<Optional<MonitoringJob>> second = executor.submit(() -> {
                ready.countDown();
                start.await();

                return jobClaimService.claimNextJob(
                        "worker-2",
                        Duration.ofMinutes(2)
                );
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            Optional<MonitoringJob> firstResult
                    = first.get(10, TimeUnit.SECONDS);

            Optional<MonitoringJob> secondResult
                    = second.get(10, TimeUnit.SECONDS);

            // Only one job exists, so only one worker can claim it.
            assertThat(
                    firstResult.isPresent() ^ secondResult.isPresent()
            ).isTrue();

            MonitoringJob claimedJob = firstResult
                    .or(() -> secondResult)
                    .orElseThrow();

            assertThat(claimedJob.monitorId()).isEqualTo(monitorId);

            Integer claimedCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM monitoring_worker.monitoring_jobs
                    WHERE monitor_id = ?
                      AND status = 'CLAIMED'
                    """,
                    Integer.class,
                    monitorId
            );

            assertThat(claimedCount).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void skipsJobLockedByAnotherTransaction() throws Exception {
        // The first job is due earlier, so it would normally be selected first.
        long lockedMonitorId = insertJob(-60);

        // This job should be claimed when the first row is locked.
        long availableMonitorId = insertJob(-30);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> lockHolder = executor.submit(()
                -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            jdbcTemplate.queryForObject(
                                    """
                                SELECT id
                                FROM monitoring_worker.monitoring_jobs
                                WHERE monitor_id = ?
                                FOR UPDATE
                                """,
                                    Long.class,
                                    lockedMonitorId
                            );

                            lockAcquired.countDown();

                            try {
                                if (!releaseLock.await(5, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "Timed out waiting to release row lock"
                                    );
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();

                                throw new IllegalStateException(
                                        "Row-lock test was interrupted",
                                        exception
                                );
                            }
                        })
        );

        try {
            // Do not attempt the claim until the first transaction holds its lock.
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Optional<MonitoringJob> result
                    = jobClaimService.claimNextJob(
                            "worker-skip-lock",
                            Duration.ofMinutes(2)
                    );

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().monitorId())
                    .isEqualTo(availableMonitorId);
        } finally {
            // Always release the lock, even if an assertion fails.
            releaseLock.countDown();
            executor.shutdown();
        }

        // Confirm that the lock-holding transaction completed.
        lockHolder.get(5, TimeUnit.SECONDS);
    }

    private long insertJob(long secondsFromNow) {
        long monitorId = MONITOR_IDS.incrementAndGet();

        jdbcTemplate.update(
                """
                INSERT INTO monitoring_worker.monitoring_jobs
                    (monitor_id, status, due_at)
                VALUES (
                    ?,
                    'PENDING',
                    CURRENT_TIMESTAMP
                        + (? * INTERVAL '1 second')
                )
                """,
                monitorId,
                secondsFromNow
        );

        createdMonitorIds.add(monitorId);

        return monitorId;
    }
}
