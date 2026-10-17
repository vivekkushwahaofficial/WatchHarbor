package com.watchharbor.monitor.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.watchharbor.monitor.dto.CreateMonitorRequest;
import com.watchharbor.monitor.dto.MonitorResponse;
import com.watchharbor.monitor.dto.UpdateMonitorRequest;
import com.watchharbor.monitor.entity.Monitor;
import com.watchharbor.monitor.repository.MonitorRepository;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    private MonitorService monitorService;

    @BeforeEach
    void setUp() {
        monitorService = new MonitorService(monitorRepository);
    }

    @Test
    void deleteMonitorDeletesExistingMonitor() {
        Monitor monitor = createSavedMonitor(
                1L,
                "Example Website",
                "https://example.com"
        );

        when(monitorRepository.findById(1L))
                .thenReturn(Optional.of(monitor));

        monitorService.deleteMonitor(1L);

        verify(monitorRepository, times(1)).findById(1L);
        verify(monitorRepository, times(1)).delete(monitor);
    }

    @Test
    void deleteMonitorThrowsExceptionWhenMonitorDoesNotExist() {
        when(monitorRepository.findById(999L))
                .thenReturn(Optional.empty());

        MonitorNotFoundException exception = assertThrows(
                MonitorNotFoundException.class,
                () -> monitorService.deleteMonitor(999L)
        );

        assertEquals(
                "Monitor not found with id: 999",
                exception.getMessage()
        );

        verify(monitorRepository, times(1)).findById(999L);
        verify(monitorRepository, never()).delete(any(Monitor.class));
    }

    @Test
    void createMonitorWithValidDataReturnsMonitorResponse() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Example Website",
                "https://example.com"
        );

        when(monitorRepository.save(any(Monitor.class)))
                .thenAnswer(invocation -> {
                    Monitor monitor = invocation.getArgument(0);

                    // Simulate database-generated values.
                    return createSavedMonitor(
                            1L,
                            monitor.getName(),
                            monitor.getUrl()
                    );
                });

        MonitorResponse response = monitorService.createMonitor(request);

        assertEquals(1L, response.id());
        assertEquals("Example Website", response.name());
        assertEquals("https://example.com", response.url());
        assertNotNull(response.createdAt());

        verify(monitorRepository, times(1)).save(any(Monitor.class));
    }

    @Test
    void getMonitorByIdReturnsMonitorWhenItExists() {
        Monitor monitor = createSavedMonitor(
                1L,
                "Example Website",
                "https://example.com"
        );

        when(monitorRepository.findById(1L))
                .thenReturn(Optional.of(monitor));

        MonitorResponse response = monitorService.getMonitorById(1L);

        assertEquals(1L, response.id());
        assertEquals("Example Website", response.name());
        assertEquals("https://example.com", response.url());
        assertNotNull(response.createdAt());

        verify(monitorRepository, times(1)).findById(1L);
    }

    @Test
    void getMonitorByIdThrowsExceptionWhenMonitorDoesNotExist() {
        when(monitorRepository.findById(999L))
                .thenReturn(Optional.empty());

        MonitorNotFoundException exception = assertThrows(
                MonitorNotFoundException.class,
                () -> monitorService.getMonitorById(999L)
        );

        assertEquals(
                "Monitor not found with id: 999",
                exception.getMessage()
        );

        verify(monitorRepository, times(1)).findById(999L);
    }

    @Test
    void createMonitorTrimsNameAndUrl() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "  Example Website  ",
                "  https://example.com  "
        );

        when(monitorRepository.save(any(Monitor.class)))
                .thenAnswer(invocation -> {
                    Monitor monitor = invocation.getArgument(0);

                    return createSavedMonitor(
                            2L,
                            monitor.getName(),
                            monitor.getUrl()
                    );
                });

        MonitorResponse response = monitorService.createMonitor(request);

        assertEquals("Example Website", response.name());
        assertEquals("https://example.com", response.url());
    }

    @Test
    void createMonitorRejectsInvalidUrl() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Example Website",
                "not-a-valid-url"
        );

        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> monitorService.createMonitor(request)
        );

        assertNotNull(exception.getMessage());

        verify(monitorRepository, never()).save(any(Monitor.class));
    }

    @Test
    void createMonitorRejectsNonHttpScheme() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Example Website",
                "ftp://example.com"
        );

        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> monitorService.createMonitor(request)
        );

        assertNotNull(exception.getMessage());

        verify(monitorRepository, never()).save(any(Monitor.class));
    }

    @Test
    void getAllMonitorsReturnsSavedMonitors() {
        Monitor firstMonitor = createSavedMonitor(
                1L,
                "Example Website",
                "https://example.com"
        );

        Monitor secondMonitor = createSavedMonitor(
                2L,
                "WatchHarbor",
                "https://watchharbor.example"
        );

        when(monitorRepository.findAll())
                .thenReturn(List.of(firstMonitor, secondMonitor));

        List<MonitorResponse> responses = monitorService.getAllMonitors();

        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).id());
        assertEquals("Example Website", responses.get(0).name());
        assertEquals("https://example.com", responses.get(0).url());

        assertEquals(2L, responses.get(1).id());
        assertEquals("WatchHarbor", responses.get(1).name());
        assertEquals("https://watchharbor.example", responses.get(1).url());

        assertNotNull(responses.get(0).createdAt());
        assertNotNull(responses.get(1).createdAt());

        verify(monitorRepository, times(1)).findAll();
    }

    @Test
    void getAllMonitorsReturnsEmptyListWhenNoMonitorsExist() {
        when(monitorRepository.findAll()).thenReturn(List.of());

        List<MonitorResponse> responses = monitorService.getAllMonitors();

        assertTrue(responses.isEmpty());
        verify(monitorRepository, times(1)).findAll();
    }

    @Test
    void updateMonitorUpdatesExistingMonitor() {
        Monitor monitor = createSavedMonitor(
                1L,
                "Old Website",
                "https://old.example.com"
        );

        when(monitorRepository.findById(1L))
                .thenReturn(Optional.of(monitor));

        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Updated Website",
                "https://example.com"
        );

        MonitorResponse response = monitorService.updateMonitor(1L, request);

        assertEquals(1L, response.id());
        assertEquals("Updated Website", response.name());
        assertEquals("https://example.com", response.url());
        assertNotNull(response.createdAt());

        verify(monitorRepository, times(1)).findById(1L);
    }

    @Test
    void updateMonitorTrimsNameAndUrl() {
        Monitor monitor = createSavedMonitor(
                2L,
                "Old Website",
                "https://old.example.com"
        );

        when(monitorRepository.findById(2L))
                .thenReturn(Optional.of(monitor));

        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "  Updated Website  ",
                "  https://example.com  "
        );

        MonitorResponse response = monitorService.updateMonitor(2L, request);

        assertEquals("Updated Website", response.name());
        assertEquals("https://example.com", response.url());
    }

    @Test
    void updateMonitorRejectsInvalidUrlWithoutChangingMonitor() {
        Monitor monitor = createSavedMonitor(
                3L,
                "Original Website",
                "https://original.example.com"
        );

        when(monitorRepository.findById(3L))
                .thenReturn(Optional.of(monitor));

        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Changed Website",
                "not-a-valid-url"
        );

        assertThrows(
                InvalidMonitorUrlException.class,
                () -> monitorService.updateMonitor(3L, request)
        );

        assertEquals("Original Website", monitor.getName());
        assertEquals(
                "https://original.example.com",
                monitor.getUrl()
        );

        verify(monitorRepository, times(1)).findById(3L);
    }

    @Test
    void updateMonitorThrowsExceptionWhenMonitorDoesNotExist() {
        when(monitorRepository.findById(999L))
                .thenReturn(Optional.empty());

        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Updated Website",
                "https://example.com"
        );

        MonitorNotFoundException exception = assertThrows(
                MonitorNotFoundException.class,
                () -> monitorService.updateMonitor(999L, request)
        );

        assertEquals(
                "Monitor not found with id: 999",
                exception.getMessage()
        );

        verify(monitorRepository, times(1)).findById(999L);
        verify(monitorRepository, never()).save(any(Monitor.class));
    }

    @Test
    void getAllMonitorsDoesNotModifyMonitorData() {
        Monitor monitor = createSavedMonitor(
                3L,
                "My Monitor",
                "https://example.org"
        );

        when(monitorRepository.findAll()).thenReturn(List.of(monitor));

        List<MonitorResponse> responses = monitorService.getAllMonitors();

        assertEquals("My Monitor", responses.get(0).name());
        assertEquals("https://example.org", responses.get(0).url());

        verify(monitorRepository, times(1)).findAll();
        verify(monitorRepository, never()).save(any(Monitor.class));
    }

    private Monitor createSavedMonitor(Long id, String name, String url) {
        Monitor monitor = new Monitor(name, url);

        // Use reflection only in this test helper to simulate JPA-generated fields.
        try {
            var idField = Monitor.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(monitor, id);

            var createdAtField = Monitor.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(monitor, Instant.now());

            return monitor;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
