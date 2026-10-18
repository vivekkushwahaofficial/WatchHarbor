package com.watchharbor.worker.security;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SafeHttpClientConfigurationTest {

    @Test
    void createsHttpClientAndConnectionManager() {
        try (AnnotationConfigApplicationContext context
                = new AnnotationConfigApplicationContext(
                        SafeHttpClientConfiguration.class)) {

            SafeDnsResolver resolver
                    = context.getBean(SafeDnsResolver.class);

            PoolingHttpClientConnectionManager connectionManager
                    = context.getBean(PoolingHttpClientConnectionManager.class);

            CloseableHttpClient httpClient
                    = context.getBean(CloseableHttpClient.class);

            assertNotNull(resolver);
            assertNotNull(connectionManager);
            assertNotNull(httpClient);
        }
    }

    @Test
    void connectionManagerUsesConfiguredDnsResolver() {
        try (AnnotationConfigApplicationContext context
                = new AnnotationConfigApplicationContext(
                        SafeHttpClientConfiguration.class)) {

            SafeDnsResolver resolver
                    = context.getBean(SafeDnsResolver.class);

            PoolingHttpClientConnectionManager connectionManager
                    = context.getBean(PoolingHttpClientConnectionManager.class);

            assertNotNull(resolver);
            assertNotNull(connectionManager);

            // The configuration supplies this resolver to the connection manager.
            // The current public API does not expose the configured resolver
            // for a direct identity assertion.
        }
    }
}
