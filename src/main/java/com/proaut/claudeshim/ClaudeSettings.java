package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patches the "theme" key in ~/.claude/settings.json on each shim launch.
 * If no theme is configured, the theme key is removed from settings.json entirely.
 */
final class ClaudeSettings {

    private static final Logger log = LoggerFactory.getLogger(ClaudeSettings.class);
    private static final Pattern THEME_PATTERN =
            Pattern.compile("(\"theme\"\\s*:\\s*\"[^\"]*\"\\s*,?\\s*\\n?|,?\\s*\\n?\\s*\"theme\"\\s*:\\s*\"[^\"]*\")", Pattern.DOTALL);

    private ClaudeSettings() {}

    static Path defaultClaudeDir() {
        return Path.of(System.getProperty("user.home", ""), ".claude");
    }

    static Path globalSettingsPath() {
        return settingsPath(defaultClaudeDir());
    }

    static Path settingsPath(Path claudeDir) {
        return claudeDir.resolve("settings.json");
    }

    static void applyTheme(String theme) {
        applyTheme(defaultClaudeDir(), theme);
    }

    static void applyTheme(Path claudeDir, String theme) {
        patch(claudeDir, json -> setTheme(json, theme), "Applied theme '" + theme + "'");
    }

    static void removeTheme() {
        removeTheme(defaultClaudeDir());
    }

    static void removeTheme(Path claudeDir) {
        patch(claudeDir, ClaudeSettings::stripTheme, "Removed theme from settings");
    }

    private static void patch(Path claudeDir, java.util.function.UnaryOperator<String> transform, String logMsg) {
        Path global = settingsPath(claudeDir);
        if (!Files.exists(global)) return;
        try {
            Files.writeString(global, transform.apply(Files.readString(global)));
            log.info(logMsg);
        } catch (IOException e) {
            log.warn("Could not patch settings.json: {}", e.getMessage());
        }
    }

    static String setTheme(String json, String theme) {
        if (json.contains("\"theme\"")) {
            return json.replaceAll("(\"theme\"\\s*:\\s*\")([^\"]*)(\")", "$1" + Matcher.quoteReplacement(theme) + "$3");
        }
        int last = json.lastIndexOf('}');
        if (last < 0) return json;
        return json.substring(0, last).stripTrailing() + ",\n  \"theme\": \"" + theme + "\"\n}";
    }

    /**
     * Reads the "overrides.claude" hex color from a custom theme file.
     * theme must be in "custom:name" format; returns null for built-in themes or missing files.
     */
    static String readThemeColor(String theme) {
        return readThemeColor(defaultClaudeDir(), theme);
    }

    static String readThemeColor(Path claudeDir, String theme) {
        if (theme == null || !theme.startsWith("custom:")) return null;
        String name = theme.substring("custom:".length());
        Path themeFile = claudeDir.resolve("themes").resolve(name + ".json");
        if (!Files.exists(themeFile)) return null;
        try {
            Matcher m = Pattern.compile("\"claude\"\\s*:\\s*\"(#[0-9a-fA-F]{6})\"")
                               .matcher(Files.readString(themeFile));
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static String stripTheme(String json) {
        // Remove "theme": "...", (trailing comma variant)
        String result = json.replaceAll(",?\\s*\\n?\\s*\"theme\"\\s*:\\s*\"[^\"]*\"\\s*,?", "");
        // Clean up double commas or leading comma after {
        result = result.replaceAll("\\{\\s*,", "{");
        result = result.replaceAll(",\\s*}", "}");
        return result;
    }
}
