package com.watchharbor.monitor.service;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpMonitorClient {

    private static final long TOTAL_DEADLINE_MILLIS = 10_000;
    private static final int MAX_REDIRECTS = 3;

    private final CloseableHttpClient httpClient;
    private final ScheduledExecutorService deadlineScheduler;
    private final long totalDeadlineMillis;

// Used by the application: the deadline is 10 seconds.
    @Autowired
    public HttpMonitorClient(
            CloseableHttpClient httpClient,
            ScheduledExecutorService deadlineScheduler) {
        this(httpClient, deadlineScheduler, TOTAL_DEADLINE_MILLIS);
    }

// Used by tests to set a shorter deadline.
    HttpMonitorClient(
            CloseableHttpClient httpClient,
            ScheduledExecutorService deadlineScheduler,
            long totalDeadlineMillis) {

        if (totalDeadlineMillis <= 0) {
            throw new IllegalArgumentException(
                    "Deadline must be greater than zero");
        }

        this.httpClient = httpClient;
        this.deadlineScheduler = deadlineScheduler;
        this.totalDeadlineMillis = totalDeadlineMillis;
    }

    public CheckResult check(String rawUrl) {
        long startedAt = System.nanoTime();
        URI currentUri;

        try {
            currentUri = parseAndValidate(rawUrl);
        } catch (InvalidDestinationException exception) {
            return failure(0, startedAt, "INVALID_URL");
        }

        long deadlineAt = startedAt
                + TimeUnit.MILLISECONDS.toNanos(totalDeadlineMillis);

        AtomicBoolean deadlineExceeded = new AtomicBoolean(false);
        AtomicReference<HttpUriRequestBase> activeRequest
                = new AtomicReference<>();

        long remainingMillis = Math.max(
                1,
                TimeUnit.NANOSECONDS.toMillis(deadlineAt - System.nanoTime()));

        ScheduledFuture<?> deadlineTask = deadlineScheduler.schedule(() -> {
            deadlineExceeded.set(true);

            HttpUriRequestBase request = activeRequest.get();
            if (request != null) {
                request.cancel();
            }
        }, remainingMillis, TimeUnit.MILLISECONDS);

        int redirectsFollowed = 0;
        int lastStatusCode = 0;

        try {
            while (true) {
                if (isDeadlineExceeded(
                        deadlineAt, deadlineExceeded, activeRequest)) {
                    return failure(lastStatusCode, startedAt, "TIMEOUT");
                }

                HttpGet request = new HttpGet(currentUri);
                request.setHeader("User-Agent", "WatchHarbor/0.1");
                request.setHeader("Accept", "*/*");

                activeRequest.set(request);

                // Handle a race where the deadline expires before registration.
                if (isDeadlineExceeded(
                        deadlineAt, deadlineExceeded, activeRequest)) {
                    return failure(lastStatusCode, startedAt, "TIMEOUT");
                }

                URI nextUri;

                try (Closeable responseResource = (Closeable) httpClient.executeOpen(
                        null, request, HttpClientContext.create())) {

                    ClassicHttpResponse response
                            = (ClassicHttpResponse) responseResource;

                    lastStatusCode = response.getCode();

                    if (isDeadlineExceeded(
                            deadlineAt, deadlineExceeded, activeRequest)) {
                        return failure(lastStatusCode, startedAt, "TIMEOUT");
                    }

                    if (!isRedirect(lastStatusCode)) {
                        boolean successful
                                = lastStatusCode >= 200 && lastStatusCode < 400;

                        return new CheckResult(
                                lastStatusCode,
                                elapsedMillis(startedAt),
                                successful,
                                successful ? null : "HTTP_STATUS_NOT_SUCCESSFUL");
                    }

                    if (redirectsFollowed >= MAX_REDIRECTS) {
                        return failure(
                                lastStatusCode,
                                startedAt,
                                "REDIRECT_LIMIT_EXCEEDED");
                    }

                    Header locationHeader
                            = response.getFirstHeader("Location");

                    if (locationHeader == null
                            || locationHeader.getValue() == null
                            || locationHeader.getValue().isBlank()) {
                        return failure(
                                lastStatusCode,
                                startedAt,
                                "INVALID_REDIRECT");
                    }

                    URI redirectUri;

                    try {
                        redirectUri = resolveAndValidateRedirect(
                                currentUri,
                                locationHeader.getValue());
                    } catch (InvalidDestinationException exception) {
                        return failure(
                                lastStatusCode,
                                startedAt,
                                "REDIRECT_REJECTED");
                    }

                    if (redirectUri == null) {
                        return failure(
                                lastStatusCode,
                                startedAt,
                                "INVALID_REDIRECT");
                    }

// Never allow an HTTPS monitor to downgrade to HTTP.
                    if ("https".equalsIgnoreCase(currentUri.getScheme())
                            && "http".equalsIgnoreCase(redirectUri.getScheme())) {
                        return failure(
                                lastStatusCode,
                                startedAt,
                                "HTTPS_DOWNGRADE_REJECTED");
                    }

                    nextUri = redirectUri;

                } catch (IOException exception) {
                    boolean timedOut = isDeadlineExceeded(
                            deadlineAt, deadlineExceeded, activeRequest);

                    return failure(
                            lastStatusCode,
                            startedAt,
                            timedOut ? "TIMEOUT" : "REQUEST_FAILED");
                } finally {
                    activeRequest.compareAndSet(request, null);
                }

                if (isDeadlineExceeded(
                        deadlineAt, deadlineExceeded, activeRequest)) {
                    return failure(lastStatusCode, startedAt, "TIMEOUT");
                }

                currentUri = nextUri;
                redirectsFollowed++;
            }
        } finally {
            deadlineTask.cancel(false);
        }
    }

    private static URI parseAndValidate(String rawUrl)
            throws InvalidDestinationException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidDestinationException();
        }

        try {
            return validateTarget(new URI(rawUrl));
        } catch (URISyntaxException exception) {
            throw new InvalidDestinationException();
        }
    }

    private static URI resolveAndValidateRedirect(
            URI currentUri, String location)
            throws InvalidDestinationException {
        try {
            URI reference = new URI(location);
            URI resolved = currentUri.resolve(reference);

            return validateTarget(resolved);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new InvalidDestinationException();
        }
    }

    private static URI validateTarget(URI uri)
            throws InvalidDestinationException {
        if (uri == null || !uri.isAbsolute() || uri.isOpaque()) {
            throw new InvalidDestinationException();
        }

        String scheme = uri.getScheme();

        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {
            throw new InvalidDestinationException();
        }

        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new InvalidDestinationException();
        }

        int port = uri.getPort();
        if (port == 0 || port > 65535) {
            throw new InvalidDestinationException();
        }

        try {
            // Reuse the URL policy for both the original URL and redirects.
            MonitorUrlValidator.validate(uri.toString());
        } catch (RuntimeException exception) {
            throw new InvalidDestinationException();
        }

        return uri;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static boolean isDeadlineExceeded(
            long deadlineAt,
            AtomicBoolean deadlineExceeded,
            AtomicReference<HttpUriRequestBase> activeRequest) {

        if (deadlineExceeded.get()
                || System.nanoTime() - deadlineAt >= 0) {
            deadlineExceeded.set(true);

            HttpUriRequestBase request = activeRequest.get();
            if (request != null) {
                request.cancel();
            }

            return true;
        }

        return false;
    }

    private static CheckResult failure(
            int statusCode, long startedAt, String reason) {
        return new CheckResult(
                statusCode,
                elapsedMillis(startedAt),
                false,
                reason);
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
    }

    public record CheckResult(
            int statusCode,
            long responseTimeMillis,
            boolean successful,
            String failureReason) {

    }

    private static final class InvalidDestinationException extends Exception {

        private static final long serialVersionUID = 1L;
    }
}
