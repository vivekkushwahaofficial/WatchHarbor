package com.watchharbor.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMonitorRequest(

        @NotBlank(message = "Monitor name is required")
        @Size(max = 100, message = "Monitor name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Monitor URL is required")
        @Size(max = 2048, message = "Monitor URL must not exceed 2048 characters")
        String url

) {
}