package com.watchharbor.monitor.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record MonitorPageResponse(
        List<MonitorResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
        ) {

    public static MonitorPageResponse from(Page<MonitorResponse> page) {
        return new MonitorPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
