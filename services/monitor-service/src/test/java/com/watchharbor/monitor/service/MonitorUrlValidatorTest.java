package com.watchharbor.monitor.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MonitorUrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com",
        "http://example.com",
        "https://example.com/path?status=ok",
        "https://[2606:4700:4700::1111]"
    })
    void acceptsAllowedUrlForms(String url) {
        assertDoesNotThrow(() -> MonitorUrlValidator.validate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1",
        "http://10.0.0.1",
        "http://172.16.0.1",
        "http://192.168.1.10",
        "http://169.254.169.254",
        "http://100.64.0.1",
        "http://192.0.2.1",
        "http://224.0.0.1",
        "http://localhost",
        "http://service.local",
        "http://service.internal",
        "http://service.home.arpa",
        "http://127.1",
        "http://2130706433",
        "http://0177.0.0.1",
        "http://0x7f000001",
        "http://[::1]",
        "http://[fc00::1]",
        "http://[fe80::1]",
        "http://[2001:db8::1]",
        "http://127.0.0.1.",
        "http://192.168.1.10.",
        "http://169.254.169.254."
    })
    void rejectsProhibitedDestinationForms(String url) {
        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> MonitorUrlValidator.validate(url)
        );

        assertNotNull(exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://example.com",
        "not-a-url",
        "http://example.com:0",
        "http://example.com:99999",
        "https:///missing-host",
        "https://user:password@example.com"
    })
    void rejectsInvalidUrlForms(String url) {
        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> MonitorUrlValidator.validate(url)
        );

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsUnresolvableNumericIpNotation() {
        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> MonitorUrlValidator.validate("http://999.999.999.999")
        );

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsIpv6DocumentationAddressWhenValidatingUrl() {
        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> MonitorUrlValidator.validate("https://[3fff::1]/")
        );

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsIpv6DocumentationAddressAfterDnsResolution()
            throws java.net.UnknownHostException {
        var address = java.net.InetAddress.getByName("3fff::1");

        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> MonitorUrlValidator.validateResolvedAddress(address)
        );

        assertNotNull(exception.getMessage());
    }

    @Test
    void rejectsNullUrl() {
        InvalidMonitorUrlException exception = assertThrows(
                InvalidMonitorUrlException.class,
                () -> MonitorUrlValidator.validate(null)
        );

        assertNotNull(exception.getMessage());
    }
}
