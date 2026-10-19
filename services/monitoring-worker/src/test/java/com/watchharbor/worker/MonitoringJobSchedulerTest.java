package com.watchharbor.worker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.springframework.scheduling.annotation.Scheduled;

class MonitoringJobSchedulerTest {

    @Test
    void invokesProcessor() {
        MonitoringJobProcessor processor
                = mock(MonitoringJobProcessor.class);

        MonitoringJobScheduler scheduler
                = new MonitoringJobScheduler(processor);

        scheduler.processNextJob();

        verify(processor).processNextJob();
    }

    @Test
    void logsUnexpectedRuntimeExceptionWithoutPropagatingIt() {
        MonitoringJobProcessor processor
                = mock(MonitoringJobProcessor.class);

        doThrow(new IllegalStateException("Simulated processing failure"))
                .when(processor).processNextJob();

        MonitoringJobScheduler scheduler
                = new MonitoringJobScheduler(processor);

        assertDoesNotThrow(scheduler::processNextJob);

        verify(processor).processNextJob();
    }

    @Test
    void configuresExpectedPollingInterval() throws NoSuchMethodException {
        Scheduled scheduled = MonitoringJobScheduler.class
                .getMethod("processNextJob")
                .getAnnotation(Scheduled.class);

        org.junit.jupiter.api.Assertions.assertNotNull(scheduled);
        org.junit.jupiter.api.Assertions.assertEquals(
                "${worker.polling-interval-ms:1000}",
                scheduled.fixedDelayString()
        );
    }
}
