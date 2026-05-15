package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathEnvironmentTest {

    @Test
    void parsesPathMappingsFromConfig() {
        Config config = Main.parseConfig("""
                paths.customer-a=/work/customer-a,/srv/customer-a
                paths.customer-b=/work/customer-b
                """);

        assertEquals(2, config.envPaths.size());
        assertEquals(List.of("/work/customer-a", "/srv/customer-a"), config.envPaths.get("customer-a"));
        assertEquals(List.of("/work/customer-b"), config.envPaths.get("customer-b"));
    }

    @Test
    void ignoresEmptyPathEntriesInList() {
        Config config = Main.parseConfig("""
                paths.customer-a=/work/customer-a, , ,/srv/customer-a
                """);

        assertEquals(List.of("/work/customer-a", "/srv/customer-a"), config.envPaths.get("customer-a"));
    }

    @Test
    void ignoresPathsKeyWithEmptyEnvName() {
        Config config = Main.parseConfig("""
                paths.=/work/orphan
                paths.real=/work/real
                """);

        assertEquals(1, config.envPaths.size());
        assertTrue(config.envPaths.containsKey("real"));
    }

    @Test
    void emptyConfigHasEmptyEnvPaths() {
        Config config = Main.parseConfig("https_proxy=http://x\n");
        assertNotNull(config.envPaths);
        assertTrue(config.envPaths.isEmpty());
    }

    @Test
    void findsEnvironmentByExactPath() {
        Path cwd = Path.of("/work/customer-a").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("/work/customer-a"));

        String result = Main.findEnvironmentByPath(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void findsEnvironmentInSubdirectory() {
        Path cwd = Path.of("/work/customer-a/project/src").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("/work/customer-a"));

        String result = Main.findEnvironmentByPath(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void returnsNullWhenNoPathMatches() {
        Path cwd = Path.of("/work/customer-c").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of(
                "customer-a", List.of("/work/customer-a"),
                "customer-b", List.of("/work/customer-b"));

        String result = Main.findEnvironmentByPath(cwd, mappings);

        assertNull(result);
    }

    @Test
    void doesNotMatchSiblingDirectoryWithSamePrefix() {
        Path cwd = Path.of("/work/customer-a2/sub").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("/work/customer-a"));

        String result = Main.findEnvironmentByPath(cwd, mappings);

        assertNull(result);
    }

    @Test
    void longestPrefixWinsOnOverlap() {
        Path cwd = Path.of("/work/shared/customer-a/sub").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of(
                "shared", List.of("/work/shared"),
                "customer-a", List.of("/work/shared/customer-a"));

        String result = Main.findEnvironmentByPath(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void picksFirstWhenMultiplePathsMatchSameEnv() {
        Path cwd = Path.of("/srv/customer-a/data").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of(
                "customer-a", List.of("/work/customer-a", "/srv/customer-a"));

        String result = Main.findEnvironmentByPath(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void returnsNullForEmptyMappings() {
        assertNull(Main.findEnvironmentByPath(Path.of("/work").toAbsolutePath(), Map.of()));
    }

    @Test
    void returnsNullForNullCwd() {
        assertNull(Main.findEnvironmentByPath(null, Map.of("a", List.of("/x"))));
    }

    @Test
    void expandsTildeInMappedPath() {
        String home = System.getProperty("user.home");
        assumeNotBlank(home);

        Path cwd = Path.of(home, "work", "customer-a").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("~/work/customer-a"));

        String result = Main.findEnvironmentByPath(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void resolvesByPathInsteadOfPrompting() throws Exception {
        Path tempDir = Files.createTempDirectory("env-path-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve("customer-a.properties"), "env.KEY=value-a\n");
        Files.writeString(envsDir.resolve("customer-b.properties"), "env.KEY=value-b\n");

        Path cwd = tempDir.resolve("work").resolve("customer-a").resolve("sub");
        Files.createDirectories(cwd);

        Map<String, List<String>> mappings = Map.of(
                "customer-a", List.of(tempDir.resolve("work").resolve("customer-a").toString()));

        Environment env = Main.resolveEnvironment(null, envsDir, mappings, cwd);

        assertNotNull(env);
        assertEquals("customer-a", env.name);
    }

    @Test
    void explicitEnvFlagOverridesPathMapping() throws Exception {
        Path tempDir = Files.createTempDirectory("env-path-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve("customer-a.properties"), "env.KEY=value-a\n");
        Files.writeString(envsDir.resolve("customer-b.properties"), "env.KEY=value-b\n");

        Path cwd = tempDir.resolve("work").resolve("customer-a");
        Files.createDirectories(cwd);

        Map<String, List<String>> mappings = Map.of(
                "customer-a", List.of(cwd.toString()));

        Environment env = Main.resolveEnvironment("customer-b", envsDir, mappings, cwd);

        assertNotNull(env);
        assertEquals("customer-b", env.name);
    }

    @Test
    void pathMappingToMissingEnvironmentDoesNotCrash() throws Exception {
        Path tempDir = Files.createTempDirectory("env-path-test");
        Path envsDir = Files.createDirectory(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve("only.properties"), "env.KEY=only\n");

        Path cwd = tempDir.resolve("work");
        Files.createDirectories(cwd);

        Map<String, List<String>> mappings = Map.of("does-not-exist", List.of(cwd.toString()));

        Environment env = Main.resolveEnvironment(null, envsDir, mappings, cwd);

        assertNotNull(env);
        assertEquals("only", env.name);
    }

    private static void assumeNotBlank(String s) {
        if (s == null || s.isBlank()) {
            org.junit.jupiter.api.Assumptions.abort("user.home not set");
        }
    }
}
