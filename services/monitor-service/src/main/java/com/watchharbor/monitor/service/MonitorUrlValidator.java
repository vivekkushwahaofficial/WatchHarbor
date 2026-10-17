package com.watchharbor.monitor.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

public final class MonitorUrlValidator {

    private static final List<Cidr> BLOCKED_IPV4_RANGES = List.of(
            cidr(0, 0, 0, 0, 8),
            cidr(10, 0, 0, 0, 8),
            cidr(100, 64, 0, 0, 10),
            cidr(127, 0, 0, 0, 8),
            cidr(169, 254, 0, 0, 16),
            cidr(172, 16, 0, 0, 12),
            cidr(192, 0, 0, 0, 24),
            cidr(192, 0, 2, 0, 24),
            cidr(192, 88, 99, 0, 24),
            cidr(192, 168, 0, 0, 16),
            cidr(198, 18, 0, 0, 15),
            cidr(198, 51, 100, 0, 24),
            cidr(203, 0, 113, 0, 24),
            cidr(224, 0, 0, 0, 4),
            cidr(240, 0, 0, 0, 4)
    );

    private MonitorUrlValidator() {
    }

    public static void validate(String url) {
        URI uri;

        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw new InvalidMonitorUrlException(
                    "Monitor URL has an invalid format"
            );
        }

        String scheme = uri.getScheme();

        boolean validScheme = scheme != null
                && (scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"));

        String host = uri.getHost();

        if (!validScheme
                || host == null
                || host.isBlank()
                || uri.getUserInfo() != null) {
            throw new InvalidMonitorUrlException(
                    "URL must be an absolute HTTP or HTTPS URL "
                    + "with a valid host and no embedded credentials"
            );
        }

        validateHost(host);
    }

    private static void validateHost(String rawHost) {
        String host = rawHost;

        // Remove brackets around IPv6 literals.
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        host = host.toLowerCase(Locale.ROOT);

        // Normalize a trailing DNS dot before any hostname/IP checks.
        String normalizedHost = host.endsWith(".")
                ? host.substring(0, host.length() - 1)
                : host;

        if (isLocalHostname(normalizedHost)) {
            throw blockedDestination();
        }

        if (normalizedHost.contains(":")) {
            validateIpv6Literal(normalizedHost);
            return;
        }

        if (isNumericAddressNotation(normalizedHost)) {
            byte[] address = parseCanonicalIpv4(normalizedHost);

            // Reject ambiguous or prohibited numeric IP addresses.
            if (address == null || isBlockedIpv4(address)) {
                throw blockedDestination();
            }
        }
    }

    private static boolean isLocalHostname(String host) {
        boolean isSingleLabelHostname
                = !host.contains(".") && !host.contains(":");

        return isSingleLabelHostname
                || host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".home.arpa")
                || host.endsWith(".lan");
    }

    private static boolean isNumericAddressNotation(String host) {
        return host.matches(
                "(?i)(?:[0-9]+|0x[0-9a-f]+)"
                + "(?:\\.(?:[0-9]+|0x[0-9a-f]+))*"
        );
    }

    private static byte[] parseCanonicalIpv4(String host) {
        String[] octets = host.split("\\.", -1);

        if (octets.length != 4) {
            return null;
        }

        byte[] address = new byte[4];

        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];

            // Disallow leading zeros to avoid ambiguous IP notation.
            if (!octet.matches("0|[1-9][0-9]{0,2}")) {
                return null;
            }

            int value;

            try {
                value = Integer.parseInt(octet);
            } catch (NumberFormatException exception) {
                return null;
            }

            if (value > 255) {
                return null;
            }

            address[index] = (byte) value;
        }

        return address;
    }

    private static boolean isBlockedIpv4(byte[] address) {
        try {
            InetAddress inetAddress = InetAddress.getByAddress(address);

            if (inetAddress.isAnyLocalAddress()
                    || inetAddress.isLoopbackAddress()
                    || inetAddress.isLinkLocalAddress()
                    || inetAddress.isSiteLocalAddress()
                    || inetAddress.isMulticastAddress()) {
                return true;
            }
        } catch (UnknownHostException exception) {
            return true;
        }

        return BLOCKED_IPV4_RANGES.stream()
                .anyMatch(range -> matchesPrefix(address, range));
    }

    private static void validateIpv6Literal(String host) {
        // Scoped IPv6 addresses are not accepted as monitor destinations.
        if (host.contains("%")) {
            throw blockedDestination();
        }

        final InetAddress resolved;

        try {
            // A colon-containing host is parsed as an IPv6 literal.
            // Hostnames are never resolved by this method.
            resolved = InetAddress.getByName(host);
        } catch (UnknownHostException exception) {
            throw blockedDestination();
        }

        if (!(resolved instanceof Inet6Address)) {
            throw blockedDestination();
        }

        byte[] address = resolved.getAddress();

        // Conservatively allow only global-unicast IPv6 addresses.
        boolean globalUnicast = (address[0] & 0xE0) == 0x20;

        // Exclude special-purpose 2001::/23 and deprecated 6to4.
        boolean specialPurpose2001 = (address[0] & 0xFF) == 0x20
                && (address[1] & 0xFF) == 0x01
                && ((address[2] & 0xFF) & 0xFE) == 0;

        boolean sixToFour = (address[0] & 0xFF) == 0x20
                && (address[1] & 0xFF) == 0x02;
        boolean documentationRange2001Db8
                = (address[0] & 0xFF) == 0x20
                && (address[1] & 0xFF) == 0x01
                && (address[2] & 0xFF) == 0x0D
                && (address[3] & 0xFF) == 0xB8;

        if (!globalUnicast
                || specialPurpose2001
                || sixToFour
                || documentationRange2001Db8
                || resolved.isAnyLocalAddress()
                || resolved.isLoopbackAddress()
                || resolved.isLinkLocalAddress()
                || resolved.isSiteLocalAddress()
                || resolved.isMulticastAddress()) {
            throw blockedDestination();
        }
    }

    private static boolean matchesPrefix(byte[] address, Cidr cidr) {
        int wholeBytes = cidr.prefixLength() / 8;
        int remainingBits = cidr.prefixLength() % 8;
        byte[] network = cidr.network();

        for (int index = 0; index < wholeBytes; index++) {
            if (address[index] != network[index]) {
                return false;
            }
        }

        if (remainingBits == 0) {
            return true;
        }

        int mask = (0xFF << (8 - remainingBits)) & 0xFF;

        return ((address[wholeBytes] & 0xFF) & mask)
                == ((network[wholeBytes] & 0xFF) & mask);
    }

    private static Cidr cidr(
            int a, int b, int c, int d, int prefixLength) {
        return new Cidr(
                new byte[]{
                    (byte) a,
                    (byte) b,
                    (byte) c,
                    (byte) d
                },
                prefixLength
        );
    }

    private static InvalidMonitorUrlException blockedDestination() {
        return new InvalidMonitorUrlException(
                "Monitor URL contains a prohibited destination address"
        );
    }

    private record Cidr(byte[] network, int prefixLength) {

    }
}
