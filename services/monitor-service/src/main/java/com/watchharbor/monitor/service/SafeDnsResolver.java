package com.watchharbor.monitor.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.DnsResolver;

public final class SafeDnsResolver implements DnsResolver, AutoCloseable {

    private static final long DNS_TIMEOUT_MILLIS = 2_000;
    private static final int MAX_CONCURRENT_DNS_LOOKUPS = 4;

    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    @FunctionalInterface
    interface HostAddressLookup {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostAddressLookup addressLookup;
    private final ThreadPoolExecutor lookupExecutor;

    public SafeDnsResolver() {
        this(InetAddress::getAllByName);
    }

    SafeDnsResolver(HostAddressLookup addressLookup) {
        this.addressLookup = Objects.requireNonNull(addressLookup);
        this.lookupExecutor = createBoundedExecutor();
    }

    private static ThreadPoolExecutor createBoundedExecutor() {
        return new ThreadPoolExecutor(
                MAX_CONCURRENT_DNS_LOOKUPS,
                MAX_CONCURRENT_DNS_LOOKUPS,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                task -> {
                    Thread thread = new Thread(
                            task,
                            "watchharbor-dns-lookup-"
                            + THREAD_NUMBER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        // Reject prohibited hostnames before starting DNS resolution.
        try {
            MonitorUrlValidator.validateHost(host);
        } catch (InvalidMonitorUrlException exception) {
            throw rejectedDestination();
        }

        final Future<InetAddress[]> lookupFuture;

        try {
            lookupFuture = lookupExecutor.submit(
                    () -> addressLookup.resolve(host));
        } catch (RejectedExecutionException exception) {
            // Fail closed when all DNS workers are occupied.
            throw rejectedDestination();
        }

        final InetAddress[] addresses;

        try {
            addresses = lookupFuture.get(
                    DNS_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);

        } catch (TimeoutException exception) {
            lookupFuture.cancel(true);
            throw resolutionFailure(
                    "Monitor host resolution timed out",
                    exception);

        } catch (InterruptedException exception) {
            lookupFuture.cancel(true);
            Thread.currentThread().interrupt();

            throw resolutionFailure(
                    "Monitor host resolution was interrupted",
                    exception);

        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof UnknownHostException) {
                throw resolutionFailure(
                        "Unable to resolve monitor host",
                        cause);
            }

            // Do not expose resolver internals to callers.
            throw rejectedDestination();
        }

        if (addresses == null || addresses.length == 0) {
            throw rejectedDestination();
        }

        // Validate every result, not just the first address.
        InetAddress[] validatedAddresses = addresses.clone();

        for (InetAddress address : validatedAddresses) {
            try {
                MonitorUrlValidator.validateResolvedAddress(address);
            } catch (InvalidMonitorUrlException exception) {
                throw rejectedDestination();
            }
        }

        return validatedAddresses;
    }

    @Override
    public String resolveCanonicalHostname(String host)
            throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw rejectedDestination();
        }

        // Avoid a separate canonical-hostname DNS lookup.
        return host;
    }

    @Override
    public void close() {
        // Cancellation is best-effort for operating-system DNS calls.
        // The pool remains bounded even if a worker cannot stop promptly.
        lookupExecutor.shutdownNow();
    }

    private static UnknownHostException resolutionFailure(
            String message, Throwable cause) {
        UnknownHostException failure = new UnknownHostException(message);
        failure.initCause(cause);
        return failure;
    }

    private static UnknownHostException rejectedDestination() {
        return new UnknownHostException(
                "Host resolution failed or returned a prohibited destination");
    }
}
