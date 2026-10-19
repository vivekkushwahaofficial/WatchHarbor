package com.watchharbor.worker.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

public final class MonitorDestinationValidator {

    private MonitorDestinationValidator() {
    }

    /**
     * Validates URL structure and its scheme. DNS resolution and resolved-IP
     * validation happen separately.
     */
    public static URI validateUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Monitor URL must not be blank");
        }

        final URI uri;

        try {
            uri = new URI(value.trim()).parseServerAuthority();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid monitor URL", exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are allowed");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Monitor URL must contain a valid host");
        }

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("URL credentials are not allowed");
        }

        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Invalid monitor URL port");
        }

        String host = uri.getHost();
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        validateNumericHostname(normalizedHost);

        // Validate IP literals without resolving ordinary hostnames.
        if (normalizedHost.contains(":")) {
            try {
                InetAddress literalAddress = InetAddress.getByName(normalizedHost);

                if (literalAddress instanceof Inet6Address) {
                    validateResolvedAddress(literalAddress);
                }
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException(
                        "Invalid IP address literal", exception);
            }
        } else if (normalizedHost.matches("[0-9.]+")) {
            try {
                InetAddress literalAddress = InetAddress.getByName(normalizedHost);

                if (!(literalAddress instanceof Inet4Address)) {
                    throw new IllegalArgumentException("Invalid IPv4 address literal");
                }

                validateResolvedAddress(literalAddress);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException(
                        "Invalid IPv4 address literal", exception);
            }
        }
        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.equals("metadata.google.internal")) {
            throw new IllegalArgumentException("Local or metadata destinations are not allowed");
        }

        return uri;
    }

    /**
     * Rejects IP addresses that must not be contacted by the monitoring worker.
     * Call this for every IP address returned by DNS.
     */
    public static void validateResolvedAddress(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("Resolved address must not be null");
        }

        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            throw new IllegalArgumentException("Private or special IP destination is not allowed");
        }

        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {
            validateIpv4(bytes);
        } else if (address instanceof Inet6Address) {
            validateIpv6(bytes);
        } else {
            throw new IllegalArgumentException("Unsupported IP address type");
        }
    }

    private static void validateNumericHostname(String host) {
        // Numeric-only hosts must use canonical dotted-decimal IPv4.
        if (host.matches("[0-9.]+")) {
            String[] octets = host.split("\\.", -1);

            if (octets.length != 4) {
                throw new IllegalArgumentException(
                        "Ambiguous numeric hostname is not allowed");
            }

            for (String octet : octets) {
                // Reject empty octets and leading zeros such as 0177.
                if (octet.isEmpty()
                        || (octet.length() > 1 && octet.startsWith("0"))) {
                    throw new IllegalArgumentException(
                            "Non-canonical IPv4 address is not allowed");
                }

                final int value;

                try {
                    value = Integer.parseInt(octet);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "Invalid IPv4 address literal", exception);
                }

                if (value > 255) {
                    throw new IllegalArgumentException(
                            "Invalid IPv4 address octet");
                }
            }

            return;
        }

        // Reject hexadecimal integer-form IPv4 addresses.
        if (host.matches("(?i)0x[0-9a-f]+")) {
            throw new IllegalArgumentException(
                    "Hexadecimal numeric hostname is not allowed");
        }
    }

    private static void validateIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);

        // 0.0.0.0/8
        if (first == 0) {
            rejectAddress();
        }

        // RFC 1918 private networks
        if (first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)) {
            rejectAddress();
        }

        // Carrier-grade NAT: 100.64.0.0/10
        if (first == 100 && second >= 64 && second <= 127) {
            rejectAddress();
        }

        // Loopback and link-local
        if (first == 127 || (first == 169 && second == 254)) {
            rejectAddress();
        }

        // Protocol assignments, documentation, benchmarking and multicast/reserved ranges
        if ((first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113)
                || first >= 224) {
            rejectAddress();
        }
    }

    private static void validateIpv6(byte[] bytes) {
        // IPv6 unique-local addresses: fc00::/7
        int first = Byte.toUnsignedInt(bytes[0]);
        if ((first & 0xFE) == 0xFC) {
            rejectAddress();
        }

        // IPv6 documentation range: 2001:db8::/32
        if (Byte.toUnsignedInt(bytes[0]) == 0x20
                && Byte.toUnsignedInt(bytes[1]) == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0D
                && Byte.toUnsignedInt(bytes[3]) == 0xB8) {
            rejectAddress();
        }

        // IPv4-mapped IPv6 addresses can disguise an IPv4 destination.
        boolean mapped = true;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                mapped = false;
                break;
            }
        }

        if (mapped && bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF) {
            byte[] ipv4 = new byte[4];
            System.arraycopy(bytes, 12, ipv4, 0, 4);
            validateIpv4(ipv4);
        }

        // IPv6 discard-only range: 100::/64.
        // Check the first 64 bits, not merely the first 16 bits.
        if (Byte.toUnsignedInt(bytes[0]) == 0x01
                && Byte.toUnsignedInt(bytes[1]) == 0x00
                && bytes[2] == 0
                && bytes[3] == 0
                && bytes[4] == 0
                && bytes[5] == 0
                && bytes[6] == 0
                && bytes[7] == 0) {
            rejectAddress();
        }
    }

    private static void rejectAddress() {
        throw new IllegalArgumentException("Private or special IP destination is not allowed");
    }
}
