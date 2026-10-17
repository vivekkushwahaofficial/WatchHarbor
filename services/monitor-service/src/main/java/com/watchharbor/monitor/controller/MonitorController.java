package com.watchharbor.monitor.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.watchharbor.monitor.dto.CreateMonitorRequest;
import com.watchharbor.monitor.dto.MonitorResponse;
import com.watchharbor.monitor.dto.UpdateMonitorRequest;
import com.watchharbor.monitor.service.MonitorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public List<MonitorResponse> getAllMonitors() {
        return monitorService.getAllMonitors();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorResponse createMonitor(
            @Valid @RequestBody CreateMonitorRequest request) {
        return monitorService.createMonitor(request);
    }

    @GetMapping("/{id}")
    public MonitorResponse getMonitorById(@PathVariable Long id) {
        return monitorService.getMonitorById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMonitor(@PathVariable Long id) {
        monitorService.deleteMonitor(id);
    }

    @PutMapping("/{id}")
    public MonitorResponse updateMonitor(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMonitorRequest request) {
        return monitorService.updateMonitor(id, request);
    }
}
