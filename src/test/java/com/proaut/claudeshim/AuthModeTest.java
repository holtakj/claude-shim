package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthModeTest {

    private static Environment load(Path tempDir, String name, String content) throws Exception {
        Path envsDir = Files.createDirectories(tempDir.resolve("envs"));
        Files.writeString(envsDir.resolve(name + ".properties"), content);
        return EnvironmentLoader.listEnvironments(envsDir).stream()
                .filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void defaultsToApiKey() throws Exception {
        Environment env = load(Files.createTempDirectory("auth"), "a", "env.ANTHROPIC_API_KEY=k\n");
        assertEquals(AuthMode.API_KEY, env.auth());
        assertNull(env.configDir());
    }

    @Test
    void loginGetsDefaultProfileDir() throws Exception {
        Path root = Files.createTempDirectory("auth");
        Environment env = load(root, "privat", "auth=login\n");
        assertEquals(AuthMode.LOGIN, env.auth());
        assertEquals(root.resolve("profiles").resolve("privat").toAbsolutePath(), env.configDir());
    }

    @Test
    void explicitConfigDirWins() throws Exception {
        Path root = Files.createTempDirectory("auth");
        Path custom = root.resolve("custom");
        Environment env = load(root, "privat", "auth=LOGIN\nconfig_dir=" + custom + "\n");
        assertEquals(custom.toAbsolutePath().normalize(), env.configDir());
    }

    @Test
    void invalidAuthModeSkipsEnvironment() throws Exception {
        assertNull(load(Files.createTempDirectory("auth"), "bad", "auth=oauth\n"));
    }

    @Test
    void loginRemovesInheritedKeysAndSetsConfigDir() throws Exception {
        Path dir = Files.createTempDirectory("profile").resolve("p");
        Environment env = new Environment("privat", new Config(), Map.of(), AuthMode.LOGIN, dir);
        Map<String, String> childEnv = new HashMap<>(Map.of(
                "ANTHROPIC_API_KEY", "sk-global", "ANTHROPIC_AUTH_TOKEN", "t", "PATH", "/bin"));

        Main.applyEnvironment(env, childEnv);

        assertFalse(childEnv.containsKey("ANTHROPIC_API_KEY"));
        assertFalse(childEnv.containsKey("ANTHROPIC_AUTH_TOKEN"));
        assertEquals("/bin", childEnv.get("PATH"));
        assertEquals(dir.toString(), childEnv.get("CLAUDE_CONFIG_DIR"));
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void loginIgnoresKeyInExtraEnvVars() throws Exception {
        Path dir = Files.createTempDirectory("profile");
        Environment env = new Environment("privat", new Config(),
                Map.of("ANTHROPIC_API_KEY", "sk-x", "FOO", "bar"), AuthMode.LOGIN, dir);
        Map<String, String> childEnv = new HashMap<>();

        Main.applyEnvironment(env, childEnv);

        assertFalse(childEnv.containsKey("ANTHROPIC_API_KEY"));
        assertEquals("bar", childEnv.get("FOO"));
    }

    @Test
    void apiKeyModeSetsKeyAndNoConfigDir() {
        Environment env = new Environment("a", new Config(), Map.of("ANTHROPIC_API_KEY", "sk-a"));
        Map<String, String> childEnv = new HashMap<>();

        Main.applyEnvironment(env, childEnv);

        assertEquals("sk-a", childEnv.get("ANTHROPIC_API_KEY"));
        assertFalse(childEnv.containsKey("CLAUDE_CONFIG_DIR"));
    }

    @Test
    void settingsAndThemeUseGivenClaudeDir() throws Exception {
        Path dir = Files.createTempDirectory("claudedir");
        Files.writeString(dir.resolve("settings.json"), "{\n  \"a\": 1\n}");
        Files.createDirectories(dir.resolve("themes"));
        Files.writeString(dir.resolve("themes").resolve("x.json"), "{\"overrides\":{\"claude\":\"#aabbcc\"}}");

        ClaudeSettings.applyTheme(dir, "custom:x");

        assertTrue(Files.readString(dir.resolve("settings.json")).contains("\"theme\": \"custom:x\""));
        assertEquals("#aabbcc", ClaudeSettings.readThemeColor(dir, "custom:x"));
    }

    @Test
    void bannerShowsAuthMode() {
        assertTrue(Banner.label("privat", AuthMode.LOGIN, "1.0").endsWith("PRIVAT [login]"));
        assertTrue(Banner.label("a", AuthMode.API_KEY, null).endsWith("A [api-key]"));
    }

    @Test
    void menuLabelMarksLogin() {
        assertEquals("p (login)", EnvResolver.menuLabel(new Environment("p", new Config(), null, AuthMode.LOGIN, Path.of("/x"))));
        assertEquals("a", EnvResolver.menuLabel(new Environment("a", new Config(), null)));
    }
}
