package com.watchharbor.worker.security;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MonitorDestinationValidatorTest {

    @Test
    void acceptsPublicHttpUrl() {
        URI uri = MonitorDestinationValidator.validateUrl(
                "https://example.com/health");

        assertEquals("https", uri.getScheme());
        assertEquals("example.com", uri.getHost());
        assertEquals("/health", uri.getPath());
    }

    @Test
    void rejectsBlankUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(" "));

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsNonHttpScheme() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "file:///etc/passwd"));

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsUrlCredentials() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "https://user:password@example.com/health"));

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsLocalhost() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "http://localhost:8080"));

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsShortenedLoopbackIpv4Url() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "http://127.1/"));
    }

    @Test
    void rejectsIntegerFormLoopbackIpv4Url() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "http://2130706433/"));
    }

    @Test
    void rejectsHexadecimalFormIpv4Url() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "http://0x7f000001/"));
    }

    @Test
    void acceptsHostnameContainingDigits() {
        URI uri = MonitorDestinationValidator.validateUrl(
                "https://api2.example.com/health");

        assertEquals("api2.example.com", uri.getHost());
    }

    @Test
    void acceptsCanonicalPublicIpv4Url() {
        URI uri = MonitorDestinationValidator.validateUrl(
                "https://8.8.8.8/health");

        assertEquals("8.8.8.8", uri.getHost());
    }

    @Test
    void rejectsIpv4OctetAbove255() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "http://999.1.1.1/"));
    }

    @Test
    void rejectsIpv4WithLeadingZeroOctet() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateUrl(
                        "http://0177.0.0.1/"));
    }

    @Test
    void rejectsIpv4ZeroNetwork() throws Exception {
        assertUnsafe("0.0.0.1");
    }

    @Test
    void rejectsPrivateIpv4In172Range() throws Exception {
        assertUnsafe("172.16.0.1");
    }

    @Test
    void rejectsIpv4ProtocolAssignmentRange() throws Exception {
        assertUnsafe("192.0.0.1");
    }

    @Test
    void rejectsIpv6UnspecifiedAddress() throws Exception {
        assertUnsafe("::");
    }

    @Test
    void rejectsIpv4MappedIpv6WithPrivateAddress() throws Exception {
        assertUnsafe("::ffff:192.168.1.1");
    }

    @Test
    void rejectsIpv6DiscardOnlyRange() throws Exception {
        assertUnsafe("100::1");
    }

    @Test
    void rejectsIpv6DiscardOnlyRangeAtEndOfPrefix() throws Exception {
        assertUnsafe("100:0:0:0:0:0:0:ffff");
    }

    @Test
    void acceptsIpv6OutsideDiscardOnlyPrefix() throws Exception {
        InetAddress address = InetAddress.getByName("100:0:0:1::1");

        assertTrue(address instanceof java.net.Inet6Address);

        MonitorDestinationValidator.validateResolvedAddress(address);
    }

    @Test
    void rejectsCarrierGradeNatIpv4() throws Exception {
        assertUnsafe("100.64.0.1");
    }

    @Test
    void rejectsBenchmarkingIpv4() throws Exception {
        assertUnsafe("198.18.0.1");
    }

    @Test
    void rejectsDocumentationIpv4() throws Exception {
        assertUnsafe("203.0.113.10");
    }

    @Test
    void rejectsIpv6DocumentationAddress() throws Exception {
        assertUnsafe("2001:db8::1");
    }

    @Test
    void rejectsIpv4MappedIpv6WithLoopbackAddress() throws Exception {
        assertUnsafe("::ffff:127.0.0.1");
    }

    @Test
    void rejectsLoopbackIpv4() throws Exception {
        assertUnsafe("127.0.0.1");
    }

    @Test
    void rejectsPrivateIpv4() throws Exception {
        assertUnsafe("192.168.1.10");
    }

    @Test
    void rejectsLinkLocalIpv4() throws Exception {
        assertUnsafe("169.254.169.254");
    }

    @Test
    void acceptsPublicIpv4() throws Exception {
        InetAddress address = InetAddress.getByAddress(
                new byte[]{(byte) 8, 8, 8, 8});

        MonitorDestinationValidator.validateResolvedAddress(address);
    }

    @Test
    void rejectsIpv6Loopback() throws Exception {
        assertUnsafe("::1");
    }

    @Test
    void rejectsIpv6UniqueLocalAddress() throws Exception {
        assertUnsafe("fc00::1");
    }

    private static void assertUnsafe(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MonitorDestinationValidator.validateResolvedAddress(resolved));

        assertTrue(exception.getMessage().contains("not allowed"));
    }
}
