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
        server.createContext("/api/v1/monitors", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            int page = 0;

            if (query != null) {
                for (String parameter : query.split("&")) {
                    String[] pair = parameter.split("=", 2);
                    if (pair.length == 2 && pair[0].equals("page")) {
                        page = Integer.parseInt(pair[1]);
                    }
                }
            }
            String json = page == 1
                    ? monitorPageWithContentJson()
                    : monitorPageJson(page, true);

            byte[] response = json.getBytes(StandardCharsets.UTF_8);

            try (exchange) {
                exchange.getResponseHeaders().add(
                        "Content-Type", "application/json"
                );
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
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

    private String monitorPageJson(int page, boolean last) {
        return """
            {
              "content": [],
              "page": %d,
              "size": 10,
              "totalElements": 0,
              "totalPages": 0,
              "first": %s,
              "last": %s
            }
            """.formatted(page, page == 0, last);
    }

    private String monitorPageWithContentJson() {
        return """
        {
          "content": [
            {
              "id": 10,
              "name": "Paginated Website",
              "url": "https://example.org",
              "createdAt": "2026-10-19T10:00:00Z",
              "checkIntervalSeconds": 600,
              "enabled": true
            }
          ],
          "page": 1,
          "size": 10,
          "totalElements": 11,
          "totalPages": 2,
          "first": false,
          "last": true
        }
        """;
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

    @Test
    void returnsMonitorPageWhenServiceRespondsSuccessfully() {
        MonitorPageResponse response = client.getMonitors(1, 10);

        assertThat(response.content()).hasSize(1);

        MonitorResponse monitor = response.content().get(0);

        assertThat(monitor.id()).isEqualTo(10L);
        assertThat(monitor.name()).isEqualTo("Paginated Website");
        assertThat(monitor.url()).isEqualTo("https://example.org");
        assertThat(monitor.checkIntervalSeconds()).isEqualTo(600);
        assertThat(monitor.enabled()).isTrue();
        assertThat(monitor.createdAt())
                .isEqualTo(Instant.parse("2026-10-19T10:00:00Z"));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(11);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
    }

    @Test
    void acceptsAnEmptyMonitorPage() {
        MonitorPageResponse response = client.getMonitors(0, 10);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.last()).isTrue();
    }

    @Test
    void rejectsInvalidPaginationArgumentsBeforeMakingARequest() {
        assertThatThrownBy(() -> client.getMonitors(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.getMonitors(0, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.getMonitors(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
