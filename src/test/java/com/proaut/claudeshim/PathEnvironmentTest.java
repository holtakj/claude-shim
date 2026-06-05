package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathEnvironmentTest {

    @Test
    void parsesPathMappingsFromConfig() {
        com.proaut.claudeshim.Main.Config config = Main.parseConfig("""
                paths.customer-a=/work/customer-a,/srv/customer-a
                paths.customer-b=/work/customer-b
                """);

        assertEquals(2, config.pathMappings().size());
        assertEquals(List.of("/work/customer-a", "/srv/customer-a"), config.pathMappings().get("customer-a"));
        assertEquals(List.of("/work/customer-b"), config.pathMappings().get("customer-b"));
    }

    @Test
    void ignoresEmptyPathEntriesInList() {
        com.proaut.claudeshim.Main.Config config = Main.parseConfig("""
                paths.customer-a=/work/customer-a, , ,/srv/customer-a
                """);

        assertEquals(List.of("/work/customer-a", "/srv/customer-a"), config.pathMappings().get("customer-a"));
    }

    @Test
    void ignoresPathsKeyWithEmptyEnvName() {
        com.proaut.claudeshim.Main.Config config = Main.parseConfig("""
                paths.=/work/orphan
                paths.real=/work/real
                """);

        assertEquals(1, config.pathMappings().size());
        assertTrue(config.pathMappings().containsKey("real"));
    }

    @Test
    void emptyConfigHasEmptyEnvPaths() {
        com.proaut.claudeshim.Main.Config config = Main.parseConfig("https_proxy=http://x\n");
        assertNotNull(config.pathMappings());
        assertTrue(config.pathMappings().isEmpty());
    }

    @Test
    void findsEnvironmentByExactPath() {
        Path cwd = Path.of("/work/customer-a").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("/work/customer-a"));

        String result = PathMapper.findMatchingEnvironment(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void findsEnvironmentInSubdirectory() {
        Path cwd = Path.of("/work/customer-a/project/src").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("/work/customer-a"));

        String result = PathMapper.findMatchingEnvironment(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void returnsNullWhenNoPathMatches() {
        Path cwd = Path.of("/work/customer-c").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of(
                "customer-a", List.of("/work/customer-a"),
                "customer-b", List.of("/work/customer-b"));

        String result = PathMapper.findMatchingEnvironment(cwd, mappings);

        assertNull(result);
    }

    @Test
    void doesNotMatchSiblingDirectoryWithSamePrefix() {
        Path cwd = Path.of("/work/customer-a2/sub").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("/work/customer-a"));

        String result = PathMapper.findMatchingEnvironment(cwd, mappings);

        assertNull(result);
    }

    @Test
    void longestPrefixWinsOnOverlap() {
        Path cwd = Path.of("/work/shared/customer-a/sub").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of(
                "shared", List.of("/work/shared"),
                "customer-a", List.of("/work/shared/customer-a"));

        String result = PathMapper.findMatchingEnvironment(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void picksMostSpecificWhenFolderIsSubfolderOfAnother() {
        // Adversarial insertion order: the less specific parent is registered first.
        // Selection must be driven by path depth, not by iteration order.
        Map<String, List<String>> mappings = new LinkedHashMap<>();
        mappings.put("parent", List.of("/work/project"));
        mappings.put("child", List.of("/work/project/sub"));

        Path cwd = Path.of("/work/project/sub/module/src").toAbsolutePath();

        assertEquals("child", PathMapper.findMatchingEnvironment(cwd, mappings));
    }

    @Test
    void picksMostSpecificAcrossSeveralNestedLevels() {
        Map<String, List<String>> mappings = new LinkedHashMap<>();
        mappings.put("level1", List.of("/work"));
        mappings.put("level2", List.of("/work/a"));
        mappings.put("level3", List.of("/work/a/b"));

        Path cwd = Path.of("/work/a/b/c/d").toAbsolutePath();

        assertEquals("level3", PathMapper.findMatchingEnvironment(cwd, mappings));
    }

    @Test
    void picksParentWhenCwdIsAboveTheSubfolder() {
        // Parent registered after the child, but cwd is not inside the child folder,
        // so only the parent mapping matches.
        Map<String, List<String>> mappings = new LinkedHashMap<>();
        mappings.put("child", List.of("/work/project/sub"));
        mappings.put("parent", List.of("/work/project"));

        Path cwd = Path.of("/work/project/other").toAbsolutePath();

        assertEquals("parent", PathMapper.findMatchingEnvironment(cwd, mappings));
    }

    @Test
    void picksFirstWhenMultiplePathsMatchSameEnv() {
        Path cwd = Path.of("/srv/customer-a/data").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of(
                "customer-a", List.of("/work/customer-a", "/srv/customer-a"));

        String result = PathMapper.findMatchingEnvironment(cwd, mappings);

        assertEquals("customer-a", result);
    }

    @Test
    void returnsNullForEmptyMappings() {
        assertNull(PathMapper.findMatchingEnvironment(Path.of("/work").toAbsolutePath(), Map.of()));
    }

    @Test
    void returnsNullForNullCwd() {
        assertNull(PathMapper.findMatchingEnvironment(null, Map.of("a", List.of("/x"))));
    }

    @Test
    void expandsTildeInMappedPath() {
        String home = System.getProperty("user.home");
        assumeNotBlank(home);

        Path cwd = Path.of(home, "work", "customer-a").toAbsolutePath();
        Map<String, List<String>> mappings = Map.of("customer-a", List.of("~/work/customer-a"));

        String result = PathMapper.findMatchingEnvironment(cwd, mappings);

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
        assertEquals("customer-a", env.name());
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
        assertEquals("customer-b", env.name());
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
        assertEquals("only", env.name());
    }

    private static void assumeNotBlank(String s) {
        if (s == null || s.isBlank()) {
            org.junit.jupiter.api.Assumptions.abort("user.home not set");
        }
    }
}
