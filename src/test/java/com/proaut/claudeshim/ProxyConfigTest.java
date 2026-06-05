package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProxyConfigTest {

    @Test
    void createsEmptyProxyConfig() {
        ProxyConfig config = ProxyConfig.empty();

        assertNull(config.httpsProxy());
        assertNull(config.httpProxy());
        assertNull(config.noProxy());
        assertNull(config.disableTelemetry());
    }

    @Test
    void createsHttpsOnlyConfig() {
        ProxyConfig config = ProxyConfig.httpsOnly("https://proxy.example:8443");

        assertEquals("https://proxy.example:8443", config.httpsProxy());
        assertNull(config.httpProxy());
        assertNull(config.noProxy());
        assertNull(config.disableTelemetry());
    }

    @Test
    void createsHttpAndHttpsConfig() {
        ProxyConfig config = ProxyConfig.withHttpAndHttps("https://proxy.example:8443", "http://proxy.example:8080");

        assertEquals("https://proxy.example:8443", config.httpsProxy());
        assertEquals("http://proxy.example:8080", config.httpProxy());
        assertNull(config.noProxy());
        assertNull(config.disableTelemetry());
    }

    @Test
    void createsConfigWithTelemetryDisabled() {
        ProxyConfig config = ProxyConfig.withTelemetryDisabled(true);

        assertNull(config.httpsProxy());
        assertNull(config.httpProxy());
        assertNull(config.noProxy());
        assertTrue(config.disableTelemetry());
    }

    @Test
    void createsConfigWithTelemetryEnabled() {
        ProxyConfig config = ProxyConfig.withTelemetryDisabled(false);

        assertNull(config.httpsProxy());
        assertNull(config.httpProxy());
        assertNull(config.noProxy());
        assertFalse(config.disableTelemetry());
    }

    @Test
    void masksPasswordInProxyUrl() {
        ProxyConfig config = ProxyConfig.httpsOnly("https://user:pass123@proxy.example:8443");

        String toString = config.toString();
        assertTrue(toString.contains("user"));
        assertFalse(toString.contains("pass123"));
        assertTrue(toString.contains("*****"));
    }

    @Test
    void masksPasswordInHttpProxyUrl() {
        ProxyConfig config = ProxyConfig.withHttpAndHttps(
            "https://user:pass123@proxy.example:8443",
            "http://user2:secret456@proxy.example:8080");

        String toString = config.toString();
        assertTrue(toString.contains("user"));
        assertTrue(toString.contains("user2"));
        assertFalse(toString.contains("pass123"));
        assertFalse(toString.contains("secret456"));
        assertTrue(toString.contains("*****"));
    }

    @Test
    void hasConfigReturnsFalseForEmpty() {
        assertFalse(ProxyConfig.empty().hasConfig());
    }

    @Test
    void hasConfigReturnsTrueWhenHttpsConfigured() {
        assertTrue(ProxyConfig.httpsOnly("https://proxy.example:8443").hasConfig());
    }

    @Test
    void hasConfigReturnsTrueWhenHttpConfigured() {
        assertTrue(ProxyConfig.withHttpAndHttps(null, "http://proxy.example:8080").hasConfig());
    }

    @Test
    void hasConfigReturnsTrueWhenNoProxyConfigured() {
        assertTrue(new ProxyConfig(null, null, "localhost", null).hasConfig());
    }

    @Test
    void hasConfigReturnsTrueWhenTelemetryConfigured() {
        assertTrue(ProxyConfig.withTelemetryDisabled(true).hasConfig());
    }
}
