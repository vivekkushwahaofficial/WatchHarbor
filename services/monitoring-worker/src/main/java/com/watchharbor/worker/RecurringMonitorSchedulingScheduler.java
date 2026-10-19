package com.watchharbor.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.watchharbor.worker.client.MonitorPageResponse;
import com.watchharbor.worker.client.MonitorResponse;
import com.watchharbor.worker.client.MonitorServiceClient;

@ConditionalOnProperty(
        name = "worker.reconciliation-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Component
public class RecurringMonitorSchedulingScheduler {

    private static final Logger log
            = LoggerFactory.getLogger(
                    RecurringMonitorSchedulingScheduler.class
            );

    private final MonitorServiceClient monitorServiceClient;
    private final RecurringMonitorSchedulingService schedulingService;
    private final int pageSize;

    public RecurringMonitorSchedulingScheduler(
            MonitorServiceClient monitorServiceClient,
            RecurringMonitorSchedulingService schedulingService,
            @Value("${worker.scheduling-page-size:100}") int pageSize
    ) {
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException(
                    "Scheduling page size must be between 1 and 100"
            );
        }

        this.monitorServiceClient = monitorServiceClient;
        this.schedulingService = schedulingService;
        this.pageSize = pageSize;
    }

    @Scheduled(
            fixedDelayString
            = "${worker.reconciliation-interval-ms:5000}"
    )
    public void reconcileDueMonitors() {
        try {
            int page = 0;

            while (true) {
                MonitorPageResponse response
                        = monitorServiceClient.getMonitors(page, pageSize);

                for (MonitorResponse monitor : response.content()) {
                    if (!monitor.enabled()) {
                        continue;
                    }

                    try {
                        schedulingService.scheduleIfDue(
                                monitor.id(),
                                monitor.checkIntervalSeconds(),
                                true
                        );
                    } catch (RuntimeException exception) {
                        log.error(
                                "Could not schedule recurring check for monitor ID {}",
                                monitor.id(),
                                exception
                        );
                    }
                }

                if (response.last() || response.content().isEmpty()) {
                    return;
                }

                page++;
            }
        } catch (RuntimeException exception) {
            // The next scheduled reconciliation will retry.
            log.error(
                    "Could not reconcile recurring monitor schedules",
                    exception
            );
        }
    }
}
