package com.watchharbor.monitor.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.watchharbor.monitor.dto.CreateMonitorRequest;
import com.watchharbor.monitor.dto.MonitorResponse;
import com.watchharbor.monitor.dto.UpdateMonitorRequest;
import com.watchharbor.monitor.service.MonitorNotFoundException;
import com.watchharbor.monitor.service.MonitorService;

@WebMvcTest(MonitorController.class)
class MonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorService monitorService;

    private MonitorResponse sampleMonitor() {
        return new MonitorResponse(
                1L,
                "Example Website",
                "https://example.com",
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }

    @Test
    void getAllMonitorsShouldReturnMonitorList() throws Exception {
        given(monitorService.getAllMonitors())
                .willReturn(List.of(sampleMonitor()));

        mockMvc.perform(get("/api/v1/monitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name")
                        .value("Example Website"))
                .andExpect(jsonPath("$[0].url")
                        .value("https://example.com"));
    }

    @Test
    void createMonitorShouldReturnCreatedMonitor() throws Exception {
        given(monitorService.createMonitor(
                org.mockito.ArgumentMatchers.any(CreateMonitorRequest.class)))
                .willReturn(sampleMonitor());

        mockMvc.perform(post("/api/v1/monitors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "name": "Example Website",
                                  "url": "https://example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name")
                        .value("Example Website"))
                .andExpect(jsonPath("$.url")
                        .value("https://example.com"));
    }

    @Test
    void createMonitorShouldReturnBadRequestWhenNameIsBlank()
            throws Exception {

        mockMvc.perform(post("/api/v1/monitors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "name": " ",
                                  "url": "https://example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.detail")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.errors")
                        .isArray());
    }

    @Test
    void getMonitorByIdShouldReturnNotFoundWhenMonitorDoesNotExist()
            throws Exception {

        given(monitorService.getMonitorById(99L))
                .willThrow(new MonitorNotFoundException(
                        "Monitor not found with id: 99"));

        mockMvc.perform(get("/api/v1/monitors/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Monitor not found with id: 99"));
    }

    @Test
    void deleteMonitorShouldReturnNoContent() throws Exception {
        willDoNothing().given(monitorService).deleteMonitor(1L);

        mockMvc.perform(delete("/api/v1/monitors/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMonitorShouldReturnNotFoundWhenMonitorDoesNotExist()
            throws Exception {

        willThrow(new MonitorNotFoundException(
                "Monitor not found with id: 99"))
                .given(monitorService).deleteMonitor(99L);

        mockMvc.perform(delete("/api/v1/monitors/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Monitor not found with id: 99"));
    }

    @Test
    void updateMonitorShouldReturnUpdatedMonitor() throws Exception {
        MonitorResponse response = new MonitorResponse(
                1L,
                "Updated Website",
                "https://example.com",
                java.time.Instant.parse("2026-01-01T00:00:00Z")
        );

        given(monitorService.updateMonitor(
                eq(1L),
                any(UpdateMonitorRequest.class)
        )).willReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/monitors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {
                              "name": "Updated Website",
                              "url": "https://example.com"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Website"))
                .andExpect(jsonPath("$.url").value("https://example.com"));
    }

    @Test
    void updateMonitorShouldRejectBlankName() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/monitors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {
                              "name": " ",
                              "url": "https://example.com"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMonitorShouldReturnNotFoundWhenMonitorDoesNotExist()
            throws Exception {
        given(monitorService.updateMonitor(
                eq(999L),
                any(UpdateMonitorRequest.class)
        )).willThrow(new MonitorNotFoundException(
                "Monitor not found with id: 999"
        ));

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/monitors/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {
                              "name": "Updated Website",
                              "url": "https://example.com"
                            }
                            """))
                .andExpect(status().isNotFound());
    }
}
