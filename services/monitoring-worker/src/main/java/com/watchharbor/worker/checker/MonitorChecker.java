package com.watchharbor.worker.checker;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import com.watchharbor.worker.security.MonitorDestinationValidator;

@Component
public class MonitorChecker {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Duration MAX_CHECK_DURATION
            = Duration.ofSeconds(10);

    /*
     * A shared daemon scheduler avoids creating a new timer thread
     * for every monitor check.
     */
    private static final ScheduledExecutorService DEADLINE_EXECUTOR
            = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(
                        task, "watchharbor-check-deadline");
                thread.setDaemon(true);
                return thread;
            });

    private final CloseableHttpClient httpClient;

    public MonitorChecker(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public MonitorCheckResult check(String targetUrl) {
        long startedAt = System.nanoTime();
        long deadline = startedAt + MAX_CHECK_DURATION.toNanos();

        try {
            URI currentUri
                    = MonitorDestinationValidator.validateUrl(targetUrl);

            boolean startedAsHttps
                    = "https".equalsIgnoreCase(currentUri.getScheme());

            for (int redirects = 0;; redirects++) {
                ensureBeforeDeadline(deadline);

                // Validate every destination, including redirect targets.
                currentUri = MonitorDestinationValidator.validateUrl(
                        currentUri.toASCIIString());

                HttpGet request = new HttpGet(currentUri);
                request.setHeader(
                        "User-Agent", "WatchHarbor-Monitor/1.0");
                request.setHeader("Accept", "*/*");

                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new CheckDeadlineExceededException();
                }

                long remainingMillis = Math.max(
                        1,
                        TimeUnit.NANOSECONDS.toMillis(remainingNanos));

                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectionRequestTimeout(
                                Timeout.ofMilliseconds(
                                        Math.min(remainingMillis, 2000)))
                        .setResponseTimeout(
                                Timeout.ofMilliseconds(
                                        Math.min(remainingMillis, 5000)))
                        .setHardCancellationEnabled(true)
                        .build();

                request.setConfig(requestConfig);

                /*
                 * Schedule cancellation for the remaining overall
                 * deadline. Keep the timer active while executing the
                 * request and consuming its response body.
                 */
                ScheduledFuture<?> deadlineTask
                        = DEADLINE_EXECUTOR.schedule(
                                request::cancel,
                                remainingNanos,
                                TimeUnit.NANOSECONDS);

                HttpHost target = new HttpHost(
                        currentUri.getScheme(),
                        currentUri.getHost(),
                        currentUri.getPort());

                try (var response = httpClient.executeOpen(target, request, null)) {

                    ensureBeforeDeadline(deadline);

                    int status = response.getCode();

                    if (isRedirect(status)) {
                        if (redirects >= MAX_REDIRECTS) {
                            return failure(
                                    MonitorCheckResult.Outcome.REJECTED,
                                    startedAt,
                                    "REDIRECT_LIMIT");
                        }

                        Header locationHeader
                                = response.getFirstHeader("Location");

                        if (locationHeader == null
                                || locationHeader.getValue().isBlank()) {
                            return new MonitorCheckResult(
                                    MonitorCheckResult.Outcome.HTTP_ERROR,
                                    status,
                                    elapsedSince(startedAt),
                                    "REDIRECT_WITHOUT_LOCATION");
                        }

                        URI location
                                = new URI(locationHeader.getValue());

                        URI nextUri = currentUri.resolve(location);
                        nextUri
                                = MonitorDestinationValidator.validateUrl(
                                        nextUri.toASCIIString());

                        if (startedAsHttps
                                && "http".equalsIgnoreCase(
                                        nextUri.getScheme())) {
                            return failure(
                                    MonitorCheckResult.Outcome.REJECTED,
                                    startedAt,
                                    "HTTPS_DOWNGRADE");
                        }

                        currentUri = nextUri;
                        continue;
                    }

                    HttpEntity entity = response.getEntity();

                    if (entity != null) {
                        try (InputStream stream = entity.getContent()) {
                            byte[] buffer = new byte[8192];
                            int total = 0;
                            int read;

                            while ((read = stream.read(buffer)) != -1) {
                                ensureBeforeDeadline(deadline);

                                if (read > MAX_RESPONSE_BYTES - total) {
                                    return failure(
                                            MonitorCheckResult.Outcome.REJECTED,
                                            startedAt,
                                            "RESPONSE_TOO_LARGE");
                                }

                                total += read;
                            }
                        }
                    }

                    // Also check the deadline after consuming the body.
                    ensureBeforeDeadline(deadline);

                    Duration elapsed = elapsedSince(startedAt);

                    if (status >= 200 && status < 400) {
                        return MonitorCheckResult.success(status, elapsed);
                    }

                    return MonitorCheckResult.httpError(status, elapsed);
                } finally {
                    /*
                     * Prevent a completed request's timer from cancelling
                     * work after its response has been processed.
                     */
                    deadlineTask.cancel(false);
                }
            }
        } catch (CheckDeadlineExceededException exception) {
            return failure(
                    MonitorCheckResult.Outcome.TIMEOUT,
                    startedAt,
                    "CHECK_TIMEOUT");
        } catch (IllegalArgumentException exception) {
            return failure(
                    MonitorCheckResult.Outcome.REJECTED,
                    startedAt,
                    "UNSAFE_DESTINATION");
        } catch (URISyntaxException exception) {
            return failure(
                    MonitorCheckResult.Outcome.REJECTED,
                    startedAt,
                    "INVALID_REDIRECT");
        } catch (IOException exception) {
            /*
             * Request cancellation may surface as IOException.
             * Classify it as a timeout if the overall deadline elapsed.
             */
            if (System.nanoTime() >= deadline) {
                return failure(
                        MonitorCheckResult.Outcome.TIMEOUT,
                        startedAt,
                        "CHECK_TIMEOUT");
            }

            return failure(
                    MonitorCheckResult.Outcome.NETWORK_ERROR,
                    startedAt,
                    "NETWORK_ERROR");
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301
                || status == 302
                || status == 303
                || status == 307
                || status == 308;
    }

    private static void ensureBeforeDeadline(long deadline) {
        if (System.nanoTime() >= deadline) {
            throw new CheckDeadlineExceededException();
        }
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private static MonitorCheckResult failure(
            MonitorCheckResult.Outcome outcome,
            long startedAt,
            String errorCode) {
        return MonitorCheckResult.failure(
                outcome, elapsedSince(startedAt), errorCode);
    }

    private static final class CheckDeadlineExceededException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}
