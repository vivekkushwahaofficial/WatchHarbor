package com.watchharbor.monitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMonitorRequest(
        @NotBlank(message = "Monitor name is required")
        @Size(max = 100, message = "Monitor name must not exceed 100 characters")
        String name,
        @NotBlank(message = "Monitor URL is required")
        @Size(max = 2048, message = "Monitor URL must not exceed 2048 characters")
        String url,
        @Min(value = 60, message = "Check interval must be at least 60 seconds")
        @Max(value = 86400, message = "Check interval must not exceed 86400 seconds")
        Integer checkIntervalSeconds,
        Boolean enabled
        ) {

    // Omitted scheduling settings remain null so updates can preserve them.
    public UpdateMonitorRequest(String name, String url) {
        this(name, url, null, null);
    }
}
