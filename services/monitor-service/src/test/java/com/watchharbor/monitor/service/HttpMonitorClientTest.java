package com.watchharbor.monitor.service;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HttpMonitorClientTest {

    private CloseableHttpClient httpClient;
    private ScheduledExecutorService deadlineScheduler;
    private HttpMonitorClient monitorClient;

    @BeforeEach
    public void setUp() {
        httpClient = org.mockito.Mockito.mock(CloseableHttpClient.class);
        deadlineScheduler = Executors.newSingleThreadScheduledExecutor();
        monitorClient = new HttpMonitorClient(httpClient, deadlineScheduler);
    }

    @AfterEach
    public void tearDown() {
        deadlineScheduler.shutdownNow();
    }

    @Test
    void rejectsLoopbackUrlWithoutMakingHttpRequest() {
        HttpMonitorClient.CheckResult result
                = monitorClient.check("http://127.0.0.1/admin");

        assertFalse(result.successful());
        assertEquals("INVALID_URL", result.failureReason());
        verifyNoInteractions(httpClient);
    }

    @Test
    void cancelsRequestWhenTotalDeadlineExpires() throws Exception {
        AtomicReference<HttpUriRequestBase> capturedRequest
                = new AtomicReference<>();

        monitorClient = new HttpMonitorClient(
                httpClient,
                deadlineScheduler,
                100);

        when(httpClient.executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class)))
                .thenAnswer(invocation -> {
                    HttpUriRequestBase request
                            = (HttpUriRequestBase) invocation.getArgument(1);

                    capturedRequest.set(request);

                    // Simulate a request that remains blocked beyond the 100 ms deadline.
                    Thread.sleep(300);

                    if (!request.isCancelled()) {
                        throw new IOException(
                                "Cancellation was not requested in time");
                    }

                    throw new IOException(
                            "Mock request cancelled by deadline");
                });

        HttpMonitorClient.CheckResult result
                = monitorClient.check("https://example.com");

        assertFalse(result.successful());
        assertEquals("TIMEOUT", result.failureReason());
        assertTrue(capturedRequest.get() != null);
        assertTrue(capturedRequest.get().isCancelled());
    }

    @Test
    void returnsSuccessfulResultForHttp200() throws Exception {
        CloseableHttpResponse response = mockResponse(200, null);

        when(httpClient.executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class)))
                .thenReturn(response);

        HttpMonitorClient.CheckResult result
                = monitorClient.check("https://example.com");

        assertEquals(200, result.statusCode());
        assertTrue(result.successful());
        assertTrue(result.responseTimeMillis() >= 0);
        assertEquals(null, result.failureReason());
        verify(httpClient, times(1)).executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class));
    }

    @Test
    void rejectsRedirectToLoopbackWithoutFollowingIt() throws Exception {
        CloseableHttpResponse response
                = mockResponse(302, "http://127.0.0.1/admin");

        when(httpClient.executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class)))
                .thenReturn(response);

        HttpMonitorClient.CheckResult result
                = monitorClient.check("https://example.com");

        assertFalse(result.successful());
        assertEquals("REDIRECT_REJECTED", result.failureReason());
        verify(httpClient, times(1)).executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class));
    }

    @Test
    void rejectsHttpsToHttpRedirect() throws Exception {
        CloseableHttpResponse response
                = mockResponse(302, "http://example.org/");

        when(httpClient.executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class)))
                .thenReturn(response);

        HttpMonitorClient.CheckResult result
                = monitorClient.check("https://example.com");

        assertFalse(result.successful());
        assertEquals(
                "HTTPS_DOWNGRADE_REJECTED",
                result.failureReason());
        verify(httpClient, times(1)).executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class));
    }

    @Test
    void stopsAfterMaximumRedirectCount() throws Exception {
        CloseableHttpResponse first
                = mockResponse(302, "https://one.example/");
        CloseableHttpResponse second
                = mockResponse(302, "https://two.example/");
        CloseableHttpResponse third
                = mockResponse(302, "https://three.example/");
        CloseableHttpResponse fourth
                = mockResponse(302, "https://four.example/");

        when(httpClient.executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class)))
                .thenReturn(first, second, third, fourth);

        HttpMonitorClient.CheckResult result
                = monitorClient.check("https://example.com");

        assertFalse(result.successful());
        assertEquals(
                "REDIRECT_LIMIT_EXCEEDED",
                result.failureReason());

        // Initial request plus three followed redirects.
        verify(httpClient, times(4)).executeOpen(
                isNull(HttpHost.class),
                any(ClassicHttpRequest.class),
                any(HttpContext.class));
    }

    private static CloseableHttpResponse mockResponse(
            int statusCode, String location) {
        CloseableHttpResponse response
                = org.mockito.Mockito.mock(CloseableHttpResponse.class);

        when(response.getCode()).thenReturn(statusCode);

        if (location != null) {
            when(response.getFirstHeader("Location"))
                    .thenReturn(new BasicHeader("Location", location));
        }

        return response;
    }
}
