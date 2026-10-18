package com.watchharbor.monitor.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class SafeDnsResolverTest {

    private final List<SafeDnsResolver> managedResolvers = new ArrayList<>();

    @AfterEach
    public void closeResolvers() {
        for (SafeDnsResolver resolver : managedResolvers) {
            resolver.close();
        }
    }

    private SafeDnsResolver createResolver(
            SafeDnsResolver.HostAddressLookup addressLookup) {
        SafeDnsResolver resolver = new SafeDnsResolver(addressLookup);
        managedResolvers.add(resolver);
        return resolver;
    }

    @Test
    void acceptsPublicIpv4AndIpv6Addresses() throws Exception {
        InetAddress ipv4 = address(
                8, 8, 8, 8);

        // Google Public DNS IPv6 address: 2001:4860:4860::8888
        InetAddress ipv6 = InetAddress.getByAddress(new byte[]{
            0x20, 0x01, 0x48, 0x60,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, (byte) 0x88, (byte) 0x88
        });

        SafeDnsResolver resolver
                = createResolver(host -> new InetAddress[]{ipv4, ipv6});

        assertArrayEquals(
                new InetAddress[]{ipv4, ipv6},
                resolver.resolve("example.com"));
    }

    @Test
    void rejectsLoopbackIpv4Address() throws Exception {
        InetAddress loopback = address(127, 0, 0, 1);

        SafeDnsResolver resolver
                = createResolver(host -> new InetAddress[]{loopback});

        assertNotNull(assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("example.com")));
    }

    @Test
    void rejectsMixedPublicAndPrivateAddresses() throws Exception {
        InetAddress publicAddress = address(8, 8, 8, 8);
        InetAddress privateAddress = address(10, 0, 0, 1);

        SafeDnsResolver resolver = createResolver(
                host -> new InetAddress[]{publicAddress, privateAddress});

        assertNotNull(assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("example.com")));
    }

    @Test
    void rejectsUniqueLocalIpv6Address() throws Exception {
        // fc00::1 is within the IPv6 unique-local range.
        InetAddress privateIpv6 = InetAddress.getByAddress(new byte[]{
            (byte) 0xFC, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 1
        });

        SafeDnsResolver resolver
                = createResolver(host -> new InetAddress[]{privateIpv6});

        assertNotNull(assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("example.com")));
    }

    @Test
    void rejectsLocalhostBeforePerformingDnsLookup() {
        AtomicBoolean lookupCalled = new AtomicBoolean(false);

        SafeDnsResolver resolver = createResolver(host -> {
            lookupCalled.set(true);
            return new InetAddress[]{address(8, 8, 8, 8)};
        });

        assertNotNull(assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("localhost")));

        assertFalse(lookupCalled.get());
    }

    @Test
    void rejectsEmptyDnsResults() {
        SafeDnsResolver resolver
                = createResolver(host -> new InetAddress[0]);

        assertNotNull(assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("example.com")));
    }

    @Test
    void rejectsDnsLookupThatExceedsTimeout() {
        SafeDnsResolver resolver = createResolver(host -> {
            try {
                // Simulate a DNS lookup that takes too long.
                Thread.sleep(3_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new UnknownHostException(
                        "Simulated DNS lookup interrupted");
            }

            return new InetAddress[]{address(8, 8, 8, 8)};
        });

        UnknownHostException exception = assertThrows(
                UnknownHostException.class,
                () -> resolver.resolve("example.com")
        );

        assertNotNull(exception.getMessage());
    }

    private static InetAddress address(int a, int b, int c, int d)
            throws UnknownHostException {
        return InetAddress.getByAddress(new byte[]{
            (byte) a, (byte) b, (byte) c, (byte) d
        });
    }
}
