package com.watchharbor.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.scheduling.annotation.Scheduled;

class LeaseRecoverySchedulerTest {

    @Test
    void invokesExpiredLeaseRecoveryWithConfiguredAttemptLimit() {
        JobClaimService jobClaimService = mock(JobClaimService.class);
        when(jobClaimService.recoverExpiredLeases(3)).thenReturn(2);

        LeaseRecoveryScheduler scheduler
                = new LeaseRecoveryScheduler(jobClaimService, 3);

        scheduler.recoverExpiredLeases();

        verify(jobClaimService).recoverExpiredLeases(3);
    }

    @Test
    void rejectsInvalidMaximumAttempts() {
        JobClaimService jobClaimService = mock(JobClaimService.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new LeaseRecoveryScheduler(jobClaimService, 0)
        );
    }

    @Test
    void configuresScheduledRecoveryWithExpectedIntervalProperty()
            throws NoSuchMethodException {

        Scheduled scheduled = LeaseRecoveryScheduler.class
                .getMethod("recoverExpiredLeases")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${worker.lease-recovery-interval-ms:10000}");
    }
}
