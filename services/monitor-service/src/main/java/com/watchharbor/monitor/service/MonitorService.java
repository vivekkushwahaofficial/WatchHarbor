package com.watchharbor.monitor.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.watchharbor.monitor.dto.CreateMonitorRequest;
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
        Monitor savedMonitor = monitorRepository.save(monitor);

        return MonitorResponse.from(savedMonitor);
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> getAllMonitors() {
        return monitorRepository.findAll()
                .stream()
                .map(MonitorResponse::from)
                .toList();
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

        return MonitorResponse.from(monitor);
    }

    private void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();

            boolean validScheme = scheme != null
                    && (scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https"));

            if (!validScheme
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw new InvalidMonitorUrlException(
                        "URL must be an absolute HTTP or HTTPS URL with a valid host"
                );
            }

        } catch (URISyntaxException exception) {
            throw new InvalidMonitorUrlException(
                    "Monitor URL has an invalid format"
            );
        }
    }
}
