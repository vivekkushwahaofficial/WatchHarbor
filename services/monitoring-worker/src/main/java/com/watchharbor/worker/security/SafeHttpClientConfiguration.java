package com.watchharbor.worker.security;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SafeHttpClientConfiguration {

    @Bean(destroyMethod = "close")
    SafeDnsResolver safeDnsResolver() {
        return new SafeDnsResolver();
    }

    @Bean(destroyMethod = "close")
    PoolingHttpClientConnectionManager monitoringConnectionManager(
            SafeDnsResolver safeDnsResolver) {

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(3))
                .setSocketTimeout(Timeout.ofSeconds(5))
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(safeDnsResolver)
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(50)
                .setMaxConnPerRoute(5)
                .build();
    }

    @Bean(destroyMethod = "close")
    CloseableHttpClient monitoringHttpClient(
            PoolingHttpClientConnectionManager monitoringConnectionManager) {

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .build();

        return HttpClients.custom()
                .setConnectionManager(monitoringConnectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableContentCompression()
                .build();
    }
}
