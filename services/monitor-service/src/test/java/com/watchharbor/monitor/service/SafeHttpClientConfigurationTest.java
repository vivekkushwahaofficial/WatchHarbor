package com.watchharbor.monitor.service;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SafeHttpClientConfigurationTest {

    @Test
    void configuredHttpClientUsesSafeDnsResolver() throws Exception {
        AtomicBoolean dnsLookupCalled = new AtomicBoolean(false);
        AtomicBoolean responseHandlerCalled = new AtomicBoolean(false);

        InetAddress loopbackAddress = InetAddress.getByAddress(
                new byte[]{127, 0, 0, 1});

        SafeHttpClientConfiguration configuration
                = new SafeHttpClientConfiguration();

        try (SafeDnsResolver testResolver = new SafeDnsResolver(host -> {
            dnsLookupCalled.set(true);
            return new InetAddress[]{loopbackAddress};
        }); CloseableHttpClient client
                = configuration.safeHttpClient(testResolver)) {

            HttpGet request = new HttpGet("http://example.invalid/");

            IOException exception = assertThrows(
                    IOException.class,
                    () -> client.execute(request, response -> {
                        responseHandlerCalled.set(true);
                        return response.getCode();
                    }));

            assertNotNull(exception);
        }

        assertTrue(
                dnsLookupCalled.get(),
                "The configured client should invoke SafeDnsResolver");

        assertFalse(
                responseHandlerCalled.get(),
                "A response should not be received for a prohibited DNS result");
    }
}
