
package com.watchharbor.worker;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.scheduling.annotation.Scheduled;

class LeaseRecoverySchedulerTest {

    @Test
    void invokesExpiredLeaseRecovery() {
        JobClaimService jobClaimService = mock(JobClaimService.class);
        when(jobClaimService.recoverExpiredLeases()).thenReturn(2);

        LeaseRecoveryScheduler scheduler =
                new LeaseRecoveryScheduler(jobClaimService);

        scheduler.recoverExpiredLeases();

        verify(jobClaimService).recoverExpiredLeases();
    }

    @Test
    void configuresScheduledRecoveryWithExpectedIntervalProperty() {
        try {
            Scheduled scheduled = LeaseRecoveryScheduler.class
                    .getMethod("recoverExpiredLeases")
                    .getAnnotation(Scheduled.class);

            assertThat(scheduled).isNotNull();
            assertThat(scheduled.fixedDelayString())
                    .isEqualTo("${worker.lease-recovery-interval-ms:10000}");
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    "Lease recovery method was not found",
                    exception
            );
        }
    }
}
