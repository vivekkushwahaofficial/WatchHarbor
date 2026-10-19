package com.watchharbor.worker;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.watchharbor.worker.client.MonitorPageResponse;
import com.watchharbor.worker.client.MonitorResponse;
import com.watchharbor.worker.client.MonitorServiceClient;

class RecurringMonitorSchedulingSchedulerTest {

    @Test
    void continuesSchedulingAfterOneMonitorFails() {
        MonitorServiceClient client = mock(MonitorServiceClient.class);
        RecurringMonitorSchedulingService service
                = mock(RecurringMonitorSchedulingService.class);

        MonitorResponse firstMonitor = monitor(101L);
        MonitorResponse secondMonitor = monitor(102L);

        when(client.getMonitors(0, 100)).thenReturn(
                new MonitorPageResponse(
                        List.of(firstMonitor, secondMonitor),
                        0,
                        100,
                        2,
                        1,
                        true,
                        true
                )
        );

        doThrow(new IllegalStateException("Simulated scheduling failure"))
                .when(service)
                .scheduleIfDue(101L, 300, true);

        RecurringMonitorSchedulingScheduler scheduler
                = new RecurringMonitorSchedulingScheduler(
                        client,
                        service,
                        100
                );

        scheduler.reconcileDueMonitors();

        verify(service).scheduleIfDue(101L, 300, true);
        verify(service).scheduleIfDue(102L, 300, true);
    }

    @Test
    void skipsDisabledMonitors() {
        MonitorServiceClient client = mock(MonitorServiceClient.class);
        RecurringMonitorSchedulingService service
                = mock(RecurringMonitorSchedulingService.class);

        MonitorResponse disabledMonitor = new MonitorResponse(
                103L,
                "Disabled monitor",
                "https://example.com",
                Instant.parse("2026-10-20T00:00:00Z"),
                300,
                false
        );

        when(client.getMonitors(0, 100)).thenReturn(
                new MonitorPageResponse(
                        List.of(disabledMonitor),
                        0,
                        100,
                        1,
                        1,
                        true,
                        true
                )
        );

        RecurringMonitorSchedulingScheduler scheduler
                = new RecurringMonitorSchedulingScheduler(
                        client,
                        service,
                        100
                );

        scheduler.reconcileDueMonitors();

        verify(service, never()).scheduleIfDue(103L, 300, false);
    }

    private MonitorResponse monitor(long id) {
        return new MonitorResponse(
                id,
                "Monitor " + id,
                "https://example.com",
                Instant.parse("2026-10-20T00:00:00Z"),
                300,
                true
        );
    }
}
