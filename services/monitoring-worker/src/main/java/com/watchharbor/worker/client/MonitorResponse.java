package com.watchharbor.worker.client;

import java.time.Instant;

public record MonitorResponse(
        Long id,
        String name,
        String url,
        Instant createdAt,
        int checkIntervalSeconds,
        boolean enabled
        ) {

}
