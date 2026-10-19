package com.watchharbor.worker.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class MonitorServiceClient {

    private final RestClient restClient;

    public MonitorServiceClient(
            @Value("${worker.monitor-service-base-url:http://localhost:8081}") String baseUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory requestFactory
                = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public MonitorResponse getMonitorById(Long monitorId) {
        if (monitorId == null || monitorId <= 0) {
            throw new IllegalArgumentException(
                    "Monitor ID must be a positive number"
            );
        }

        try {
            MonitorResponse response = restClient.get()
                    .uri("/api/v1/monitors/{id}", monitorId)
                    .retrieve()
                    .body(MonitorResponse.class);

            if (response == null) {
                throw new MonitorServiceException(
                        "Monitor Service returned an empty response"
                );
            }

            return response;

        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value()
                    == HttpStatus.NOT_FOUND.value()) {
                throw new MonitorNotFoundException(monitorId);
            }

            throw new MonitorServiceException(
                    "Monitor Service returned an unsuccessful response",
                    exception
            );

        } catch (ResourceAccessException exception) {
            throw new MonitorServiceException(
                    "Monitor Service could not be reached",
                    exception
            );
        }
    }

    public MonitorPageResponse getMonitors(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100"
            );
        }

        try {
            MonitorPageResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/monitors")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(MonitorPageResponse.class);

            if (response == null || response.content() == null) {
                throw new MonitorServiceException(
                        "Monitor Service returned an empty response"
                );
            }

            return response;

        } catch (HttpStatusCodeException exception) {
            throw new MonitorServiceException(
                    "Monitor Service returned an unsuccessful response",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new MonitorServiceException(
                    "Monitor Service could not be reached",
                    exception
            );
        }
    }

}
