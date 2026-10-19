package com.watchharbor.worker;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RecurringMonitorSchedulingServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecurringMonitorSchedulingService schedulingService;

    private static final AtomicLong IDS
            = new AtomicLong(System.currentTimeMillis() * 1_000L);

    private long monitorId;

    @BeforeEach
    void setUp() {
        monitorId = IDS.incrementAndGet();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
            DELETE FROM monitoring_worker.monitoring_jobs
            WHERE monitor_id = ?
            """, monitorId);

        jdbcTemplate.update("""
            DELETE FROM monitoring_worker.monitoring_schedule_state
            WHERE monitor_id = ?
            """, monitorId);
    }

    @Test
    void createsOnePendingJobForNewEnabledMonitor() {
        boolean scheduled
                = schedulingService.scheduleIfDue(monitorId, 60, true);

        assertThat(scheduled).isTrue();
        assertThat(countJobs()).isEqualTo(1);
        assertThat(countScheduleRows()).isEqualTo(1);
    }

    @Test
    void doesNotCreateDuplicateJobBeforeNextDueTime() {
        assertThat(
                schedulingService.scheduleIfDue(monitorId, 60, true)
        ).isTrue();

        boolean scheduledAgain
                = schedulingService.scheduleIfDue(monitorId, 60, true);

        assertThat(scheduledAgain).isFalse();
        assertThat(countJobs()).isEqualTo(1);
    }

    @Test
    void doesNotScheduleDisabledMonitor() {
        boolean scheduled
                = schedulingService.scheduleIfDue(monitorId, 60, false);

        assertThat(scheduled).isFalse();
        assertThat(countJobs()).isZero();
        assertThat(countScheduleRows()).isZero();
    }

    @Test
    void updatesIntervalAndSchedulesWhenIntervalChanges() {
        assertThat(
                schedulingService.scheduleIfDue(monitorId, 3600, true)
        ).isTrue();

        // Finish the existing job so it no longer blocks a new active job.
        jdbcTemplate.update("""
        UPDATE monitoring_worker.monitoring_jobs
        SET status = 'SUCCEEDED'
        WHERE monitor_id = ?
          AND status = 'PENDING'
        """, monitorId);

        // Change the interval. The new schedule should be due immediately.
        boolean scheduled
                = schedulingService.scheduleIfDue(monitorId, 120, true);

        assertThat(scheduled).isTrue();

        Integer interval = jdbcTemplate.queryForObject("""
        SELECT check_interval_seconds
        FROM monitoring_worker.monitoring_schedule_state
        WHERE monitor_id = ?
        """, Integer.class, monitorId);

        assertThat(interval).isEqualTo(120);

        Integer activeJobs = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM monitoring_worker.monitoring_jobs
        WHERE monitor_id = ?
          AND status IN ('PENDING', 'CLAIMED')
        """, Integer.class, monitorId);

        assertThat(activeJobs).isEqualTo(1);
    }

    @Test
    void doesNotCreateAnotherJobWhenPendingJobAlreadyExists() {
        assertThat(
                schedulingService.scheduleIfDue(monitorId, 60, true)
        ).isTrue();

        boolean scheduledAgain
                = schedulingService.scheduleIfDue(monitorId, 60, true);

        assertThat(scheduledAgain).isFalse();
        assertThat(countActiveJobs()).isEqualTo(1);
    }

    @Test
    void doesNotCreateAnotherJobWhenClaimedJobAlreadyExists() {
        assertThat(
                schedulingService.scheduleIfDue(monitorId, 60, true)
        ).isTrue();

        jdbcTemplate.update("""
            UPDATE monitoring_worker.monitoring_jobs
            SET status = 'CLAIMED',
                claimed_by = 'test-worker',
                claim_token = '550e8400-e29b-41d4-a716-446655440000',
                lease_until = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
            WHERE monitor_id = ?
              AND status = 'PENDING'
            """, monitorId);

        boolean scheduledAgain
                = schedulingService.scheduleIfDue(monitorId, 60, true);

        assertThat(scheduledAgain).isFalse();
        assertThat(countActiveJobs()).isEqualTo(1);
    }

    @Test
    void concurrentSchedulingCreatesAtMostOneActiveJob() throws Exception {
        // Create the schedule row before starting concurrent calls.
        // This makes both calls contend for the same existing row lock.
        assertThat(
                schedulingService.scheduleIfDue(monitorId, 60, true)
        ).isTrue();

        // Complete the first job so the schedule can be tested again.
        jdbcTemplate.update("""
            UPDATE monitoring_worker.monitoring_jobs
            SET status = 'SUCCEEDED'
            WHERE monitor_id = ?
              AND status = 'PENDING'
            """, monitorId);

        // Make the next execution due now.
        jdbcTemplate.update("""
            UPDATE monitoring_worker.monitoring_schedule_state
            SET next_due_at = CURRENT_TIMESTAMP
            WHERE monitor_id = ?
            """, monitorId);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> schedulingAttempt = ()
                    -> schedulingService.scheduleIfDue(
                            monitorId, 60, true
                    );

            List<Future<Boolean>> results = executor.invokeAll(
                    List.of(schedulingAttempt, schedulingAttempt)
            );

            int successfulInsertions = 0;

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulInsertions++;
                }
            }

            assertThat(successfulInsertions).isEqualTo(1);
            assertThat(countActiveJobs()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    private int countActiveJobs() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM monitoring_worker.monitoring_jobs
            WHERE monitor_id = ?
              AND status IN ('PENDING', 'CLAIMED')
            """, Integer.class, monitorId);
    }

    @Test
    void rejectsInvalidMonitorId() {
        assertThatThrownBy(
                () -> schedulingService.scheduleIfDue(0, 60, true)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Monitor ID must be positive");
    }

    @Test
    void rejectsIntervalBelowMinimum() {
        assertThatThrownBy(
                () -> schedulingService.scheduleIfDue(monitorId, 59, true)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Interval must be between 60 and 86400 seconds"
                );
    }

    @Test
    void rejectsIntervalAboveMaximum() {
        assertThatThrownBy(
                () -> schedulingService.scheduleIfDue(monitorId, 86401, true)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Interval must be between 60 and 86400 seconds"
                );
    }

    private int countJobs() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM monitoring_worker.monitoring_jobs
            WHERE monitor_id = ?
            """, Integer.class, monitorId);
    }

    private int countScheduleRows() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM monitoring_worker.monitoring_schedule_state
            WHERE monitor_id = ?
            """, Integer.class, monitorId);
    }
}
