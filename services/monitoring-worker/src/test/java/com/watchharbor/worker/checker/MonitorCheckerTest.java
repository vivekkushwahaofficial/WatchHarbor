package com.watchharbor.worker.checker;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorCheckerTest {

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloseableHttpResponse response;

    @Mock
    private HttpEntity entity;

    private MonitorChecker checker;

    @BeforeEach
    void setUp() {
        checker = new MonitorChecker(httpClient);
    }

    @Test
    void rejectsLocalhostWithoutSendingRequest() throws IOException {
        MonitorCheckResult result
                = checker.check("http://localhost:8080");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());
        assertEquals("UNSAFE_DESTINATION", result.errorCode());

        verify(httpClient, never()).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void rejectsUnsupportedSchemeWithoutSendingRequest()
            throws IOException {
        MonitorCheckResult result
                = checker.check("file:///etc/passwd");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());

        verify(httpClient, never()).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void rejectsBlankUrlWithoutSendingRequest() throws IOException {
        MonitorCheckResult result = checker.check(" ");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());

        verify(httpClient, never()).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void reportsHttpStatusForSuccessfulResponse() throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);
        when(response.getCode()).thenReturn(200);
        when(response.getEntity()).thenReturn(null);

        MonitorCheckResult result
                = checker.check("https://example.com/health");

        assertEquals(
                MonitorCheckResult.Outcome.SUCCESS,
                result.outcome());
        assertEquals(200, result.httpStatus());
        assertNotNull(result.responseTime());
        assertFalse(result.responseTime().isNegative());

        verify(httpClient).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void reportsHttpErrorForServerError() throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);
        when(response.getCode()).thenReturn(500);
        when(response.getEntity()).thenReturn(null);

        MonitorCheckResult result
                = checker.check("https://example.com/health");

        assertEquals(
                MonitorCheckResult.Outcome.HTTP_ERROR,
                result.outcome());
        assertEquals(500, result.httpStatus());
        assertEquals("HTTP_ERROR", result.errorCode());
    }

    @Test
    void rejectsInvalidUrlWithoutSendingRequest() throws IOException {
        MonitorCheckResult result = checker.check("https://");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());

        verify(httpClient, never()).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void rejectsHttpsToHttpRedirect() throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);
        when(response.getCode()).thenReturn(302);
        when(response.getFirstHeader("Location"))
                .thenReturn(new BasicHeader(
                        "Location", "http://example.com/health"));

        MonitorCheckResult result
                = checker.check("https://example.com/start");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());
        assertEquals("HTTPS_DOWNGRADE", result.errorCode());

        verify(httpClient).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void rejectsRedirectWithoutLocation() throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);
        when(response.getCode()).thenReturn(302);
        when(response.getFirstHeader("Location")).thenReturn(null);

        MonitorCheckResult result
                = checker.check("https://example.com/start");

        assertEquals(
                MonitorCheckResult.Outcome.HTTP_ERROR,
                result.outcome());
        assertEquals(302, result.httpStatus());
        assertEquals(
                "REDIRECT_WITHOUT_LOCATION",
                result.errorCode());
    }

    @Test
    void rejectsRedirectAfterMaximumRedirects() throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);
        when(response.getCode()).thenReturn(302);
        when(response.getFirstHeader("Location"))
                .thenReturn(new BasicHeader("Location", "/next"));

        MonitorCheckResult result
                = checker.check("https://example.com/start");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());
        assertEquals("REDIRECT_LIMIT", result.errorCode());

        verify(httpClient, times(4)).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void rejectsResponseLargerThan64KiB() throws IOException {
        byte[] oversizedBody = new byte[(64 * 1024) + 1];
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);
        when(response.getCode()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent())
                .thenReturn(new ByteArrayInputStream(oversizedBody));

        MonitorCheckResult result
                = checker.check("https://example.com/health");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());
        assertEquals(
                "RESPONSE_TOO_LARGE",
                result.errorCode());
    }

    @Test
    void rejectsRedirectToLocalhostWithoutSendingSecondRequest()
            throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);
        when(response.getCode()).thenReturn(302);
        when(response.getFirstHeader("Location"))
                .thenReturn(new BasicHeader(
                        "Location", "http://localhost:8080/admin"));

        MonitorCheckResult result
                = checker.check("https://example.com/start");

        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());
        assertEquals("UNSAFE_DESTINATION", result.errorCode());

        verify(httpClient, times(1)).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void rejectsRedirectToPrivateIpWithoutSendingSecondRequest()
            throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenReturn(response);

        when(response.getCode()).thenReturn(302);
        when(response.getFirstHeader("Location"))
                .thenReturn(new BasicHeader(
                        "Location", "https://192.168.1.1/admin"));

        MonitorCheckResult result
                = checker.check("https://example.com/start");
        assertEquals(
                MonitorCheckResult.Outcome.REJECTED,
                result.outcome());
        assertEquals("UNSAFE_DESTINATION", result.errorCode());

        verify(httpClient, times(1)).executeOpen(
                any(HttpHost.class), any(HttpGet.class), isNull());
    }

    @Test
    void reportsNetworkErrorWhenRequestFails() throws IOException {
        when(httpClient.executeOpen(
                any(HttpHost.class),
                any(ClassicHttpRequest.class),
                nullable(HttpContext.class)))
                .thenThrow(new IOException("Simulated connection failure"));

        MonitorCheckResult result
                = checker.check("https://example.com/health");

        assertEquals(
                MonitorCheckResult.Outcome.NETWORK_ERROR,
                result.outcome());
        assertEquals("NETWORK_ERROR", result.errorCode());
    }

}
