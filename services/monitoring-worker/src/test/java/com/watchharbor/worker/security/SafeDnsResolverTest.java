package com.watchharbor.worker.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SafeDnsResolverTest {

    @Test
    void acceptsPublicIpv4Address() throws Exception {
        InetAddress publicAddress = address("8.8.8.8");

        try (SafeDnsResolver resolver
                = new SafeDnsResolver(host -> new InetAddress[]{publicAddress})) {
            assertArrayEquals(
                    new InetAddress[]{publicAddress},
                    resolver.resolve("example.test"));
        }
    }

    @Test
    void rejectsLoopbackAddress() throws Exception {
        assertRejected(address("127.0.0.1"));
    }

    @Test
    void rejectsPrivateAddress() throws Exception {
        assertRejected(address("10.0.0.1"));
    }

    @Test
    void rejectsMixedPublicAndPrivateAnswers() throws Exception {
        InetAddress publicAddress = address("8.8.8.8");
        InetAddress privateAddress = address("192.168.1.5");

        try (SafeDnsResolver resolver = new SafeDnsResolver(
                host -> new InetAddress[]{publicAddress, privateAddress})) {
            assertThrows(
                    UnknownHostException.class,
                    () -> resolver.resolve("example.test"));
        }
    }

    @Test
    void rejectsEmptyDnsResult() {
        try (SafeDnsResolver resolver
                = new SafeDnsResolver(host -> new InetAddress[0])) {
            assertThrows(
                    UnknownHostException.class,
                    () -> resolver.resolve("example.test"));
        }
    }

    @Test
    void rejectsLocalhostBeforeDnsLookup() {
        try (SafeDnsResolver resolver = new SafeDnsResolver(host -> {
            throw new AssertionError("DNS should not be called for localhost");
        })) {
            assertThrows(
                    UnknownHostException.class,
                    () -> resolver.resolve("localhost"));
        }
    }

    @Test
    void timesOutSlowDnsLookup() {
        try (SafeDnsResolver resolver = new SafeDnsResolver(host -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            return new InetAddress[]{address("8.8.8.8")};
        })) {
            UnknownHostException exception = assertThrows(
                    UnknownHostException.class,
                    () -> resolver.resolve("example.test"));

            assertTrue(exception.getMessage().contains("timed out"));
        }
    }

    private static InetAddress address(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip);
    }

    private static void assertRejected(InetAddress address) {
        try (SafeDnsResolver resolver
                = new SafeDnsResolver(host -> new InetAddress[]{address})) {
            assertThrows(
                    UnknownHostException.class,
                    () -> resolver.resolve("example.test"));
        }
    }

    @Test
    void blockedDnsWorkersCauseBoundedRejectionAfterCallerTimeout()
            throws Exception {
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger lookupCount = new AtomicInteger();

        try (SafeDnsResolver resolver = new SafeDnsResolver(host -> {
            lookupCount.incrementAndGet();
            started.countDown();

            while (true) {
                try {
                    release.await();
                    break;
                } catch (InterruptedException ignored) {
                    // Simulate DNS lookups that ignore cancellation.
                }
            }

            return new InetAddress[]{address("8.8.8.8")};
        })) {
            ExecutorService callers = Executors.newFixedThreadPool(20);

            try {
                Future<?>[] initialRequests = new Future<?>[4];

                // Occupy all four DNS workers.
                for (int i = 0; i < initialRequests.length; i++) {
                    final int index = i;
                    initialRequests[i] = callers.submit(() -> {
                        try {
                            resolver.resolve("blocked" + index + ".example.test");
                        } catch (UnknownHostException expected) {
                            // The caller times out while the lookup stays blocked.
                        }
                    });
                }

                assertTrue(started.await(3, TimeUnit.SECONDS));

                // Wait until all initial callers have timed out.
                for (Future<?> request : initialRequests) {
                    request.get(4, TimeUnit.SECONDS);
                }

                assertEquals(4, lookupCount.get());

                // Fill the 16-slot queue while all four workers remain blocked.
                Future<?>[] queuedRequests = new Future<?>[16];

                for (int i = 0; i < queuedRequests.length; i++) {
                    final int index = i;
                    queuedRequests[i] = callers.submit(() -> {
                        try {
                            resolver.resolve("queued" + index + ".example.test");
                        } catch (UnknownHostException expected) {
                            // Expected when the lookup times out or is interrupted.
                        }
                    });
                }

                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(3);

                while (resolver.queuedTaskCountForTesting() < 16
                        && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }

                assertEquals(16, resolver.queuedTaskCountForTesting());
                assertEquals(4, lookupCount.get());

                // All workers and queue slots are occupied: reject immediately.
                UnknownHostException exception = assertThrows(
                        UnknownHostException.class,
                        () -> resolver.resolve("overflow.example.test"));

                assertTrue(exception.getMessage().contains("capacity exceeded"));
                assertEquals(4, lookupCount.get());
            } finally {
                // Release blocked lookups before shutting down callers.
                release.countDown();
                callers.shutdownNow();
            }
        }
    }

    @Test
    void timedOutLookupThatIgnoresInterruptKeepsItsWorkerOccupied()
            throws Exception {
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        AtomicBoolean lookupFinished = new AtomicBoolean(false);

        try (SafeDnsResolver resolver = new SafeDnsResolver(host -> {
            lookupStarted.countDown();

            boolean released = false;
            while (!released) {
                try {
                    releaseLookup.await();
                    released = true;
                } catch (InterruptedException ignored) {
                    // Simulate a DNS provider that does not stop on interruption.
                }
            }

            lookupFinished.set(true);
            return new InetAddress[]{address("8.8.8.8")};
        })) {
            ExecutorService callers = Executors.newSingleThreadExecutor();

            try {
                Future<?> request = callers.submit(() -> {
                    try {
                        resolver.resolve("slow.example.test");
                    } catch (UnknownHostException expected) {
                        // The caller should time out.
                    }
                });

                assertTrue(lookupStarted.await(3, TimeUnit.SECONDS));
                request.get(4, TimeUnit.SECONDS);

                assertEquals(false, lookupFinished.get());
            } finally {
                releaseLookup.countDown();
                callers.shutdownNow();
            }
        }
    }

    @Test
    void rejectsLookupWhenDnsExecutorCapacityIsExhausted() throws Exception {
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger lookupCount = new AtomicInteger();

        try (SafeDnsResolver resolver = new SafeDnsResolver(host -> {
            lookupCount.incrementAndGet();
            started.countDown();

            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new UnknownHostException("DNS lookup interrupted");
            }

            return new InetAddress[]{address("8.8.8.8")};
        })) {
            ExecutorService callers = Executors.newFixedThreadPool(20);

            try {
                Future<?>[] requests = new Future<?>[20];

                for (int i = 0; i < requests.length; i++) {
                    final int index = i;
                    requests[i] = callers.submit(() -> {
                        try {
                            resolver.resolve("host" + index + ".example.test");
                        } catch (UnknownHostException ignored) {
                            // Expected during cleanup or timeout.
                        }
                    });
                }

                assertTrue(started.await(5, TimeUnit.SECONDS));

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

                while (resolver.queuedTaskCountForTesting() < 16
                        && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }

                assertEquals(16, resolver.queuedTaskCountForTesting());
                assertEquals(4, lookupCount.get());

                UnknownHostException exception = assertThrows(
                        UnknownHostException.class,
                        () -> resolver.resolve("overflow.example.test"));

                assertTrue(exception.getMessage().contains("capacity exceeded"));
                assertEquals(4, lookupCount.get());
            } finally {
                release.countDown();
                callers.shutdownNow();
            }
        }
    }
}
