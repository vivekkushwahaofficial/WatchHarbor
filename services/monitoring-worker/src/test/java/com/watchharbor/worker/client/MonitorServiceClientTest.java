package com.watchharbor.worker.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class MonitorServiceClientTest {

    private HttpServer server;
    private MonitorServiceClient client;
    private ExecutorService executor;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);

        server.createContext("/api/v1/monitors/1", exchange -> {
            byte[] response = """
                    {
                      "id": 1,
                      "name": "Example Website",
                      "url": "https://example.com",
                      "createdAt": "2026-10-18T10:00:00Z",
                      "checkIntervalSeconds": 300,
                      "enabled": true
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            try (exchange) {
                exchange.getResponseHeaders().add(
                        "Content-Type", "application/json"
                );
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });

        server.createContext("/api/v1/monitors/2", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        server.createContext("/api/v1/monitors/3", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(503, -1);
            }
        });

        server.start();

        client = new MonitorServiceClient(
                "http://127.0.0.1:" + server.getAddress().getPort()
        );
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }

        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void returnsMonitorWhenServiceRespondsSuccessfully() {
        MonitorResponse response = client.getMonitorById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Example Website");
        assertThat(response.url()).isEqualTo("https://example.com");
        assertThat(response.checkIntervalSeconds()).isEqualTo(300);
        assertThat(response.enabled()).isTrue();
        assertThat(response.createdAt())
                .isEqualTo(Instant.parse("2026-10-18T10:00:00Z"));
    }

    @Test
    void throwsMonitorNotFoundExceptionFor404() {
        assertThatThrownBy(() -> client.getMonitorById(2L))
                .isInstanceOf(MonitorNotFoundException.class);
    }

    @Test
    void throwsMonitorServiceExceptionForOtherHttpErrors() {
        assertThatThrownBy(() -> client.getMonitorById(3L))
                .isInstanceOf(MonitorServiceException.class)
                .hasMessage(
                        "Monitor Service returned an unsuccessful response"
                );
    }

    @Test
    void throwsMonitorServiceExceptionWhenServiceCannotBeReached() {
        client = new MonitorServiceClient("http://127.0.0.1:1");

        assertThatThrownBy(() -> client.getMonitorById(1L))
                .isInstanceOf(MonitorServiceException.class)
                .hasMessage("Monitor Service could not be reached");
    }

    @Test
    void rejectsInvalidMonitorIdsBeforeMakingARequest() {
        assertThatThrownBy(() -> client.getMonitorById(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.getMonitorById(0L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.getMonitorById(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
