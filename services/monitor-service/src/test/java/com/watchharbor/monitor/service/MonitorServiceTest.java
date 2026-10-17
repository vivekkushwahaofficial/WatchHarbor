package com.watchharbor.monitor.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.watchharbor.monitor.dto.CreateMonitorRequest;
import com.watchharbor.monitor.dto.MonitorPageResponse;
import com.watchharbor.monitor.dto.MonitorResponse;
import com.watchharbor.monitor.dto.UpdateMonitorRequest;
import com.watchharbor.monitor.entity.Monitor;
import com.watchharbor.monitor.repository.MonitorRepository;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @InjectMocks
    private MonitorService monitorService;

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
    void createMonitorAcceptsHttpUrl() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Example Website",
                "http://example.com"
        );

        when(monitorRepository.save(any(Monitor.class)))
                .thenAnswer(invocation -> {
                    Monitor monitor = invocation.getArgument(0);
                    return createSavedMonitor(
                            1L,
                            monitor.getName(),
                            monitor.getUrl()
                    );
                });

        MonitorResponse response = monitorService.createMonitor(request);

        assertEquals("http://example.com", response.url());
        verify(monitorRepository).save(any(Monitor.class));
    }

    @Test
    void createMonitorRejectsUrlWithEmbeddedCredentials() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Example Website",
                "https://user:password@example.com"
        );
        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> monitorService.createMonitor(request)
        );

        assertNotNull(exception.getMessage());

        verify(monitorRepository, never()).save(any(Monitor.class));
    }

    @Test
    void createMonitorRejectsUrlWithoutHostname() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Example Website",
                "https:///path"
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
                1L, "Example Website", "https://example.com");

        Monitor secondMonitor = createSavedMonitor(
                2L, "WatchHarbor", "https://watchharbor.example");

        when(monitorRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(firstMonitor, secondMonitor),
                        PageRequest.of(0, 20),
                        2
                ));

        MonitorPageResponse response
                = monitorService.getAllMonitors(0, 20);

        assertEquals(2, response.content().size());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(2, response.totalElements());
        assertEquals(1, response.totalPages());
        assertTrue(response.first());
        assertTrue(response.last());

        assertEquals(1L, response.content().get(0).id());
        assertEquals("Example Website",
                response.content().get(0).name());

        assertEquals(2L, response.content().get(1).id());
        assertEquals("WatchHarbor",
                response.content().get(1).name());

        verify(monitorRepository).findAll(any(Pageable.class));
    }

    @Test
    void getAllMonitorsReturnsEmptyPageWhenNoMonitorsExist() {
        when(monitorRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 20),
                        0
                ));

        MonitorPageResponse response
                = monitorService.getAllMonitors(0, 20);

        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.first());
        assertTrue(response.last());

        verify(monitorRepository).findAll(any(Pageable.class));
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

        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> monitorService.updateMonitor(3L, request)
        );

        assertNotNull(exception.getMessage());

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
                3L, "My Monitor", "https://example.org");

        when(monitorRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(monitor),
                        PageRequest.of(0, 20),
                        1
                ));

        MonitorPageResponse response
                = monitorService.getAllMonitors(0, 20);

        assertEquals("My Monitor",
                response.content().get(0).name());
        assertEquals("https://example.org",
                response.content().get(0).url());

        verify(monitorRepository).findAll(any(Pageable.class));
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
