package com.watchharbor.monitor.dto;

import java.time.Instant;

import com.watchharbor.monitor.entity.Monitor;

public record MonitorResponse(
        Long id,
        String name,
        String url,
        Instant createdAt
        ) {

    public static MonitorResponse from(Monitor monitor) {
        return new MonitorResponse(
                monitor.getId(),
                monitor.getName(),
                monitor.getUrl(),
                monitor.getCreatedAt()
        );
    }
}
