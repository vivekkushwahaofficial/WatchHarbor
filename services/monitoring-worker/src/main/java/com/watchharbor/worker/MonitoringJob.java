package com.watchharbor.worker;

import java.time.OffsetDateTime;

// Represents a monitoring job successfully claimed by a worker.
public record MonitoringJob(
        long id,
        long monitorId,
        OffsetDateTime dueAt,
        String claimedBy,
        OffsetDateTime leaseUntil,
        int attemptCount
        ) {

}
