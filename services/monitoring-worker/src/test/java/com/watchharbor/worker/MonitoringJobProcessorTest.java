package com.watchharbor.worker;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.watchharbor.worker.checker.MonitorCheckResult;
import com.watchharbor.worker.checker.MonitorChecker;
import com.watchharbor.worker.client.MonitorNotFoundException;
import com.watchharbor.worker.client.MonitorResponse;
import com.watchharbor.worker.client.MonitorServiceClient;
import com.watchharbor.worker.client.MonitorServiceException;

@ExtendWith(MockitoExtension.class)
class MonitoringJobProcessorTest {

    @Mock
    private JobClaimService jobClaimService;

    @Mock
    private MonitorServiceClient monitorServiceClient;

    @Mock
    private MonitorChecker monitorChecker;

    @Mock
    private CheckResultPersistenceService resultPersistenceService;

    private MonitoringJobProcessor processor;

    private MonitoringJob job;

    private final UUID claimToken = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new MonitoringJobProcessor(
                jobClaimService,
                monitorServiceClient,
                monitorChecker,
                resultPersistenceService,
                "worker-test",
                30,
                3,
                5
        );

        job = new MonitoringJob(
                101L,
                42L,
                OffsetDateTime.now(ZoneOffset.UTC),
                "worker-test",
                OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30),
                1,
                claimToken
        );
    }

    @Test
    void returnsFalseWhenNoDueJobIsAvailable() {
        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.empty());

        assertFalse(processor.processNextJob());

        verifyNoInteractions(
                monitorServiceClient,
                monitorChecker,
                resultPersistenceService
        );
    }

    @Test
    void doesNotAssumeDisabledJobWasCompletedWhenClaimIsStale() {
        MonitorResponse monitor = new MonitorResponse(
                42L,
                "Example",
                "https://example.com",
                Instant.now(),
                60,
                false);

        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenReturn(monitor);
        when(jobClaimService.completeJob(101L, claimToken))
                .thenReturn(false);

        assertTrue(processor.processNextJob());

        verify(jobClaimService).completeJob(101L, claimToken);
        verifyNoInteractions(monitorChecker, resultPersistenceService);
    }

    @Test
    void doesNotAssumeRetryWasScheduledWhenClaimIsStale() {
        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenThrow(new MonitorServiceException("Service unavailable"));
        when(jobClaimService.retryJob(
                101L,
                claimToken,
                "MONITOR_SERVICE_UNAVAILABLE",
                Duration.ofSeconds(5)))
                .thenReturn(false);

        assertTrue(processor.processNextJob());

        verify(jobClaimService).retryJob(
                101L,
                claimToken,
                "MONITOR_SERVICE_UNAVAILABLE",
                Duration.ofSeconds(5));
        verify(jobClaimService, never())
                .failJob(anyLong(), any(), anyString());
    }

    @Test
    void doesNotAssumeJobFailedWhenClaimIsStale() {
        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenThrow(new MonitorNotFoundException(42L));
        when(jobClaimService.failJob(
                101L, claimToken, "MONITOR_NOT_FOUND"))
                .thenReturn(false);

        assertTrue(processor.processNextJob());

        verify(jobClaimService).failJob(
                101L, claimToken, "MONITOR_NOT_FOUND");
        verifyNoInteractions(monitorChecker, resultPersistenceService);
    }

    @Test
    void savesResultWhenEnabledMonitorCheckSucceeds() {
        MonitorResponse monitor = enabledMonitor();
        MonitorCheckResult result = MonitorCheckResult.success(
                200, Duration.ofMillis(42));

        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenReturn(monitor);
        when(monitorChecker.check(monitor.url()))
                .thenReturn(result);

        assertTrue(processor.processNextJob());

        verify(monitorChecker).check(monitor.url());
        verify(resultPersistenceService)
                .saveResultAndComplete(job, result);
        verify(jobClaimService, never())
                .failJob(anyLong(), any(), anyString());
        verify(jobClaimService, never())
                .retryJob(anyLong(), any(), anyString(), any());
    }

    @Test
    void completesJobWithoutCheckingWhenMonitorIsDisabled() {
        MonitorResponse monitor = new MonitorResponse(
                42L,
                "Example",
                "https://example.com",
                Instant.now(),
                60,
                false
        );

        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenReturn(monitor);
        when(jobClaimService.completeJob(101L, claimToken))
                .thenReturn(true);

        assertTrue(processor.processNextJob());

        verify(jobClaimService).completeJob(101L, claimToken);
        verifyNoInteractions(monitorChecker, resultPersistenceService);
    }

    @Test
    void failsJobWhenMonitorNoLongerExists() {
        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenThrow(new MonitorNotFoundException(42L));

        assertTrue(processor.processNextJob());

        verify(jobClaimService).failJob(
                101L, claimToken, "MONITOR_NOT_FOUND");
        verifyNoInteractions(monitorChecker, resultPersistenceService);
    }

    @Test
    void retriesWhenMonitorServiceFailsBeforeMaxAttempts() {
        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenThrow(new MonitorServiceException("Service unavailable"));

        assertTrue(processor.processNextJob());

        verify(jobClaimService).retryJob(
                101L,
                claimToken,
                "MONITOR_SERVICE_UNAVAILABLE",
                Duration.ofSeconds(5)
        );
        verify(jobClaimService, never())
                .failJob(anyLong(), any(), anyString());
    }

    @Test
    void failsJobWhenMonitorServiceFailureExhaustsAttempts() {
        MonitoringJob exhaustedJob = new MonitoringJob(
                job.id(),
                job.monitorId(),
                job.dueAt(),
                job.claimedBy(),
                job.leaseUntil(),
                3,
                claimToken
        );

        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(exhaustedJob));
        when(monitorServiceClient.getMonitorById(42L))
                .thenThrow(new MonitorServiceException("Service unavailable"));

        assertTrue(processor.processNextJob());

        verify(jobClaimService).failJob(
                101L,
                claimToken,
                "MONITOR_SERVICE_UNAVAILABLE"
        );
        verify(jobClaimService, never())
                .retryJob(anyLong(), any(), anyString(), any());
    }

    @Test
    void discardsResultWhenClaimBecomesStale() {
        MonitorResponse monitor = enabledMonitor();
        MonitorCheckResult result = MonitorCheckResult.success(
                200, Duration.ofMillis(42));

        when(jobClaimService.claimNextJob(
                "worker-test", Duration.ofSeconds(30)))
                .thenReturn(Optional.of(job));
        when(monitorServiceClient.getMonitorById(42L))
                .thenReturn(monitor);
        when(monitorChecker.check(monitor.url()))
                .thenReturn(result);
        doThrow(new StaleClaimException())
                .when(resultPersistenceService)
                .saveResultAndComplete(job, result);

        assertTrue(processor.processNextJob());

        verify(resultPersistenceService)
                .saveResultAndComplete(job, result);
        verify(jobClaimService, never())
                .retryJob(anyLong(), any(), anyString(), any());
    }

    private MonitorResponse enabledMonitor() {
        return new MonitorResponse(
                42L,
                "Example",
                "https://example.com",
                Instant.now(),
                60,
                true
        );
    }
}
