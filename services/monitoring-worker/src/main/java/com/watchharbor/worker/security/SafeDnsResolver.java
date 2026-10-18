package com.watchharbor.worker.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.DnsResolver;

public final class SafeDnsResolver implements DnsResolver, AutoCloseable {

    private static final long DNS_TIMEOUT_SECONDS = 2;

    private final ExecutorService executor;
    private final DnsLookup dnsLookup;

    public SafeDnsResolver() {
        this(InetAddress::getAllByName);
    }

    // Package-private constructor for deterministic unit tests.
    SafeDnsResolver(DnsLookup dnsLookup) {
        this.dnsLookup = Objects.requireNonNull(dnsLookup, "dnsLookup");

        this.executor = new ThreadPoolExecutor(
                4,
                4,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16),
                task -> {
                    Thread thread = new Thread(task, "watchharbor-dns");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    int queuedTaskCountForTesting() {
        if (executor instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getQueue().size();
        }

        throw new IllegalStateException("Unexpected DNS executor implementation");
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        validateHostname(host);

        final Future<InetAddress[]> future;

        try {
            future = executor.submit(() -> dnsLookup.lookup(host));
        } catch (RejectedExecutionException exception) {
            throw unknownHost("DNS lookup capacity exceeded", exception);
        }

        final InetAddress[] addresses;

        try {
            addresses = future.get(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw unknownHost("DNS lookup timed out", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw unknownHost("DNS lookup interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof UnknownHostException unknownHostException) {
                throw unknownHostException;
            }

            throw unknownHost("DNS lookup failed", cause);
        }

        if (addresses == null || addresses.length == 0) {
            throw unknownHost("DNS returned no addresses", null);
        }

        // Reject the entire answer if even one address is unsafe.
        for (InetAddress address : addresses) {
            try {
                MonitorDestinationValidator.validateResolvedAddress(address);
            } catch (IllegalArgumentException exception) {
                throw unknownHost("DNS returned a prohibited address", exception);
            }
        }

        return Arrays.copyOf(addresses, addresses.length);
    }

    @Override
    public String resolveCanonicalHostname(String host)
            throws UnknownHostException {
        resolve(host); // Validate the DNS results using our existing safeguards.
        return host;
    }

    private static void validateHostname(String host)
            throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw unknownHost("Hostname must not be blank", null);
        }

        String normalized = host.toLowerCase(Locale.ROOT);

        if (normalized.equals("localhost")
                || normalized.endsWith(".localhost")
                || normalized.equals("metadata.google.internal")) {
            throw unknownHost("Local or metadata hostname is prohibited", null);
        }
    }

    private static UnknownHostException unknownHost(
            String message, Throwable cause) {
        UnknownHostException exception = new UnknownHostException(message);

        if (cause != null) {
            exception.initCause(cause);
        }

        return exception;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    interface DnsLookup {

        InetAddress[] lookup(String hostname) throws UnknownHostException;
    }
}
