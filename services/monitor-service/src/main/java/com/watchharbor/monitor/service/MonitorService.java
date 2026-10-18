package com.watchharbor.monitor.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.watchharbor.monitor.dto.CreateMonitorRequest;
import com.watchharbor.monitor.dto.MonitorPageResponse;
import com.watchharbor.monitor.dto.MonitorResponse;
import com.watchharbor.monitor.dto.UpdateMonitorRequest;
import com.watchharbor.monitor.entity.Monitor;
import com.watchharbor.monitor.repository.MonitorRepository;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;

    public MonitorService(MonitorRepository monitorRepository) {
        this.monitorRepository = monitorRepository;
    }

    @Transactional
    public MonitorResponse createMonitor(CreateMonitorRequest request) {
        String name = request.name().trim();
        String url = request.url().trim();

        validateUrl(url);

        Monitor monitor = new Monitor(name, url);

        monitor.updateScheduling(
                request.checkIntervalSeconds(),
                request.enabled()
        );

        Monitor savedMonitor = monitorRepository.save(monitor);

        return MonitorResponse.from(savedMonitor);
    }

    @Transactional(readOnly = true)
    public MonitorPageResponse getAllMonitors(int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<MonitorResponse> monitorPage = monitorRepository
                .findAll(pageable)
                .map(MonitorResponse::from);

        return MonitorPageResponse.from(monitorPage);
    }

    @Transactional(readOnly = true)
    public MonitorResponse getMonitorById(Long id) {
        Monitor monitor = monitorRepository.findById(id)
                .orElseThrow(() -> new MonitorNotFoundException(
                        "Monitor not found with id: " + id
                ));

        return MonitorResponse.from(monitor);
    }

    @Transactional
    public void deleteMonitor(Long id) {
        Monitor monitor = monitorRepository.findById(id)
                .orElseThrow(() -> new MonitorNotFoundException(
                        "Monitor not found with id: " + id
                ));

        monitorRepository.delete(monitor);
    }

    @Transactional
    public MonitorResponse updateMonitor(Long id, UpdateMonitorRequest request) {
        Monitor monitor = monitorRepository.findById(id)
                .orElseThrow(() -> new MonitorNotFoundException(
                        "Monitor not found with id: " + id
                ));

        String name = request.name().trim();
        String url = request.url().trim();

        validateUrl(url);
        monitor.updateDetails(name, url);

        monitor.updateScheduling(
                request.checkIntervalSeconds(),
                request.enabled()
        );

        return MonitorResponse.from(monitor);
    }

    private void validateUrl(String url) {
        MonitorUrlValidator.validate(url);
    }
}
