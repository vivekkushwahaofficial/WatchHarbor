package com.watchharbor.monitor.dto;

import java.time.Instant;

import com.watchharbor.monitor.entity.Monitor;

public record MonitorResponse(
        Long id,
        String name,
        String url,
        Instant createdAt,
        int checkIntervalSeconds,
        boolean enabled
        ) {

    // Retain compatibility with existing four-argument Java callers.
    public MonitorResponse(
            Long id,
            String name,
            String url,
            Instant createdAt
    ) {
        this(id, name, url, createdAt, 300, true);
    }

    public static MonitorResponse from(Monitor monitor) {
        return new MonitorResponse(
                monitor.getId(),
                monitor.getName(),
                monitor.getUrl(),
                monitor.getCreatedAt(),
                monitor.getCheckIntervalSeconds(),
                monitor.isEnabled()
        );
    }
}
