package com.watchharbor.worker.security;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                    = context.getBean(
                            PoolingHttpClientConnectionManager.class);

            CloseableHttpClient httpClient
                    = context.getBean(CloseableHttpClient.class);

            assertNotNull(resolver);
            assertNotNull(connectionManager);
            assertNotNull(httpClient);
        }
    }

    @Test
    void httpClientUsesDnsResolverAndRejectsUnsafeAddress()
            throws Exception {

        AtomicBoolean lookupCalled = new AtomicBoolean(false);
        SafeHttpClientConfiguration configuration
                = new SafeHttpClientConfiguration();

        try (SafeDnsResolver resolver = new SafeDnsResolver(host -> {
            lookupCalled.set(true);

            // Simulate DNS returning a prohibited loopback address.
            return new InetAddress[]{
                InetAddress.getByName("127.0.0.1")
            };
        })) {
            PoolingHttpClientConnectionManager manager
                    = configuration.monitoringConnectionManager(resolver);

            try (CloseableHttpClient client
                    = configuration.monitoringHttpClient(manager)) {

                HttpHost target = new HttpHost(
                        "http", "watchharbor-dns-test.invalid", 80);

                HttpGet request = new HttpGet(
                        "http://watchharbor-dns-test.invalid/");

                assertThrows(
                        IOException.class,
                        () -> client.executeOpen(target, request, null));

                assertTrue(
                        lookupCalled.get(),
                        "The HTTP client should invoke the configured DNS resolver");
            }
        }
    }
}
