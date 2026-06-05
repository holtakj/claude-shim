package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MainConfigTest {

    @Test
    void parsesJavaPropertiesConfiguration() {
        com.proaut.claudeshim.Main.Config config = Main.parseConfig("""
                https_proxy=http://proxy.example:8443
                http_proxy=http://proxy.example:8080
                no_proxy=localhost,127.0.0.1
                disable_telemetry=yes
                """);

        assertEquals("http://proxy.example:8443", config.proxy().httpsProxy());
        assertEquals("http://proxy.example:8080", config.proxy().httpProxy());
        assertEquals("localhost,127.0.0.1", config.proxy().noProxy());
        assertEquals(Boolean.TRUE, config.proxy().disableTelemetry());
    }

    @Test
    void supportsPropertiesSyntaxWithColonAndMixedCaseKeys() {
        com.proaut.claudeshim.Main.Config config = Main.parseConfig("""
                HTTPS_PROXY: http://secure.example:8443
                Disable_Telemetry: off
                """);

        assertEquals("http://secure.example:8443", config.proxy().httpsProxy());
        assertEquals(Boolean.FALSE, config.proxy().disableTelemetry());
    }

    @Test
    void returnsEmptyConfigWhenPropertiesFileIsMissing() {
        com.proaut.claudeshim.Main.Config config = Main.loadConfig(Path.of("does-not-exist", "config.properties"));

        assertNull(config.proxy().httpsProxy());
        assertNull(config.proxy().httpProxy());
        assertNull(config.proxy().noProxy());
        assertNull(config.proxy().disableTelemetry());
    }

    @Test
    void loadsConfigurationFromPropertiesFile() throws Exception {
        Path tempFile = Files.createTempFile("claude-shim", ".properties");
        Files.writeString(tempFile, """
                https_proxy=http://proxy.example:8443
                no_proxy=localhost
                disable_telemetry=1
                """);

        com.proaut.claudeshim.Main.Config config = Main.loadConfig(tempFile);

        assertEquals("http://proxy.example:8443", config.proxy().httpsProxy());
        assertEquals("localhost", config.proxy().noProxy());
        assertEquals(Boolean.TRUE, config.proxy().disableTelemetry());
    }

    @Test
    void usesWindowsAppDataForDefaultConfigPath() {
        Path p = Main.resolveDefaultConfigPath(
                "Windows 11",
                Map.of("APPDATA", "C:/Users/user/AppData/Roaming"),
                "C:/Users/user");

        assertEquals(
                Path.of("C:/Users/user/AppData/Roaming", "claude-shim", "config.properties").normalize(),
                p.normalize());
    }

    @Test
    void usesWindowsRoamingFallbackWhenAppDataIsMissing() {
        Path p = Main.resolveDefaultConfigPath("Windows 11", Map.of(), "C:/Users/user");

        assertEquals(
                Path.of("C:/Users/user", "AppData", "Roaming", "claude-shim", "config.properties").normalize(),
                p.normalize());
    }
}
