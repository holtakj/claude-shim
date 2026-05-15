package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTest {

    @Test
    void loadsEnvironmentFromPropertiesFile() throws Exception {
        Path tempDir = Files.createTempDirectory("env-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve("customer-a.properties"), """
                https_proxy=http://proxy-a:8080
                disable_telemetry=true
                env.ANTHROPIC_API_KEY=sk-ant-key-a
                env.CUSTOM_VAR=hello
                """);

        var environments = EnvironmentLoader.listEnvironments(envsDir);

        assertEquals(1, environments.size());
        Environment env = environments.get(0);
        assertEquals("customer-a", env.name);
        assertEquals("http://proxy-a:8080", env.config.https_proxy);
        assertEquals(Boolean.TRUE, env.config.disable_telemetry);
        assertEquals("sk-ant-key-a", env.extraEnvVars.get("ANTHROPIC_API_KEY"));
        assertEquals("hello", env.extraEnvVars.get("CUSTOM_VAR"));
    }

    @Test
    void listsMultipleEnvironmentsSorted() throws Exception {
        Path tempDir = Files.createTempDirectory("env-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve("zebra.properties"), "env.KEY=z\n");
        Files.writeString(envsDir.resolve("alpha.properties"), "env.KEY=a\n");

        var environments = EnvironmentLoader.listEnvironments(envsDir);

        assertEquals(2, environments.size());
        assertEquals("alpha", environments.get(0).name);
        assertEquals("zebra", environments.get(1).name);
    }

    @Test
    void returnsEmptyListWhenEnvsDirMissing() {
        var environments = EnvironmentLoader.listEnvironments(Path.of("/nonexistent/envs"));
        assertTrue(environments.isEmpty());
    }

    @Test
    void resolveEnvironmentByName() throws Exception {
        Path tempDir = Files.createTempDirectory("env-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve("prod.properties"), "env.ANTHROPIC_API_KEY=sk-prod\n");
        Files.writeString(envsDir.resolve("dev.properties"), "env.ANTHROPIC_API_KEY=sk-dev\n");

        Environment env = Main.resolveEnvironment("prod", envsDir);

        assertNotNull(env);
        assertEquals("prod", env.name);
        assertEquals("sk-prod", env.extraEnvVars.get("ANTHROPIC_API_KEY"));
    }

    @Test
    void resolveEnvironmentAutoSelectsWhenOnlyOne() throws Exception {
        Path tempDir = Files.createTempDirectory("env-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve("only.properties"), "env.ANTHROPIC_API_KEY=sk-only\n");

        Environment env = Main.resolveEnvironment(null, envsDir);

        assertNotNull(env);
        assertEquals("only", env.name);
    }

    @Test
    void resolveEnvironmentReturnsNullWhenNoEnvs() throws Exception {
        Path tempDir = Files.createTempDirectory("env-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));

        Environment env = Main.resolveEnvironment(null, envsDir);

        assertNull(env);
    }
}
