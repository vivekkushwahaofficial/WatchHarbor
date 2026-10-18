package com.watchharbor.monitor.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SafeHttpClientConfiguration {

    @Bean(destroyMethod = "close")
    public SafeDnsResolver safeDnsResolver() {
        return new SafeDnsResolver();
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient safeHttpClient(
            SafeDnsResolver safeDnsResolver) {

        // Limits the time spent establishing and using a connection.
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(3))
                .setSocketTimeout(Timeout.ofSeconds(5))
                .setTimeToLive(TimeValue.ofMinutes(1))
                .build();

        // Apply DNS validation when the connection manager resolves hosts.
        PoolingHttpClientConnectionManager connectionManager
                = PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(safeDnsResolver)
                        .setDefaultConnectionConfig(connectionConfig)
                        .setMaxConnTotal(50)
                        .setMaxConnPerRoute(5)
                        .build();

        // Set finite pool-acquisition and response timeouts.
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .setRedirectsEnabled(false)
                .setContentCompressionEnabled(false)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableContentCompression()
                .disableCookieManagement()
                .build();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService httpRequestDeadlineScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(
                    task,
                    "watchharbor-http-deadline"
            );
            thread.setDaemon(true);
            return thread;
        });
    }
}
