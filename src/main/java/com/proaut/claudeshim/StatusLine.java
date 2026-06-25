package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages Claude Code's {@code statusLine} setting in ~/.claude/settings.json.
 *
 * <p>Claude Code's TUI uses ANSI escape sequences that overwrite any status bar
 * drawn directly to the terminal. Claude Code provides a built-in {@code statusLine}
 * feature: a shell script that receives JSON session data on stdin and prints
 * whatever it wants to stdout. Claude Code renders that output in a persistent
 * bar at the bottom of the TUI, immune to escape sequence garbling.</p>
 *
 * <p>This class installs a small shell script at {@code ~/.claude/claudeshim-statusline.sh}
 * that prints the selected environment name (passed via the {@code CLAUDE_SHIM_ENV}
 * environment variable). It configures the {@code statusLine} field in
 * {@code settings.json} to point to that script, and sets the
 * {@code CLAUDE_SHIM_ENV} variable before launching Claude.</p>
 *
 * <p>Works on Linux, macOS, and Windows via GraalVM native image.</p>
 */
final class StatusLine {

    private static final Logger log = LoggerFactory.getLogger(StatusLine.class);
    private static final String CLAUDE_DIR = Path.of(System.getProperty("user.home", ""), ".claude").toString();
    private static final String SCRIPT_NAME = "claudeshim-statusline.sh";
    private static final String SCRIPT_PATH = CLAUDE_DIR + "/" + SCRIPT_NAME;
    private static final String ENV_VAR = "CLAUDE_SHIM_ENV";

    // Pattern to match the statusLine object in JSON (handles multiline)
    private static final Pattern STATUS_LINE_PATTERN = Pattern.compile(
            "(\"statusLine\"\\s*:\\s*)\\{[^}]*\\}",
            Pattern.DOTALL
    );

    private StatusLine() {}

    /**
     * Installs the status line script and configures Claude Code to use it.
     *
     * @param envName the environment name to display (e.g. "PRODUCTION")
     */
    static void apply(String envName) {
        if (!isInteractive()) {
            return;
        }
        try {
            installScript();
            configureStatusLine(envName);
            log.info("Set statusLine for environment: {}", envName);
        } catch (IOException e) {
            log.warn("Could not configure statusLine: {}", e.getMessage());
        }
    }

    /**
     * Removes the statusLine configuration from Claude's settings.json.
     * Does NOT delete the script file (it may be reused).
     */
    static void remove() {
        if (!isInteractive()) {
            return;
        }
        Path settings = ClaudeSettings.globalSettingsPath();
        if (!Files.exists(settings)) {
            return;
        }
        try {
            String json = Files.readString(settings);
            String updated = stripStatusLine(json);
            Files.writeString(settings, updated);
            log.info("Removed statusLine from settings");
        } catch (IOException e) {
            log.warn("Could not patch settings.json statusLine: {}", e.getMessage());
        }
    }

    /**
     * Returns true when running in an interactive terminal.
     */
    private static boolean isInteractive() {
        return System.console() != null;
    }

    /**
     * Creates the status line shell script in ~/.claude/.
     * The script reads the CLAUDE_SHIM_ENV environment variable and prints it.
     * Idempotent — only writes if the file doesn't exist or content differs.
     */
    static void installScript() throws IOException {
        Path dir = Path.of(CLAUDE_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path script = Path.of(SCRIPT_PATH);

        String content = """
                #!/bin/sh
                # claudeshim-statusline.sh — prints the selected environment name
                # Installed by claude-shim; do not edit manually.
                echo "▶ ${CLAUDE_SHIM_ENV}"
                """;

        if (Files.exists(script)) {
            String existing = Files.readString(script).strip();
            if (existing.equals(content.strip())) {
                return; // Already installed and unchanged
            }
        }
        Files.writeString(script, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        script.toFile().setExecutable(true);
    }

    /**
     * Configures (or updates) the statusLine field in settings.json.
     *
     * <p>The statusLine field points to the installed script.
     * If the field already exists, its command is updated.
     * If it doesn't exist, it is appended.</p>
     */
    static void configureStatusLine(String envName) throws IOException {
        Path settings = ClaudeSettings.globalSettingsPath();
        if (!Files.exists(settings)) {
            log.debug("No Claude settings file at {}, skipping statusLine", settings);
            return;
        }
        String json = Files.readString(settings);
        String updated = buildStatusLineJson(json, SCRIPT_PATH);
        Files.writeString(settings, updated);
    }

    /**
     * Builds a JSON string with the statusLine field set to the given script path.
     * Used both for file-based configuration and for unit testing.
     */
    static String buildStatusLineJson(String json, String scriptPath) {
        String statusLineObj = "\"statusLine\": {\"type\": \"command\", \"command\": \"" + scriptPath + "\"}";
        Matcher m = STATUS_LINE_PATTERN.matcher(json);
        if (m.find()) {
            return m.replaceFirst(statusLineObj);
        }
        // Append before closing brace
        int lastBrace = json.lastIndexOf('}');
        if (lastBrace < 0) {
            return json + statusLineObj + "\n}";
        }
        String before = json.substring(0, lastBrace).stripTrailing();
        boolean hadContent = before.length() > 1;
        return before
                + (hadContent ? ",\n" : "\n")
                + "  " + statusLineObj + "\n}";
    }

    /**
     * Removes the statusLine field from the JSON string.
     * Handles both trailing-comma and end-of-file variants.
     */
    static String stripStatusLine(String json) {
        // Match the full statusLine object: "statusLine": {...}
        String result = json.replaceAll(
                "\"statusLine\"\\s*:\\s*\\{[^}]*\\}\\s*,\\s*\\n?", "");
        result = result.replaceAll(
                "(?<!,)\\s*\\n?\\s*\"statusLine\"\\s*:\\s*\\{[^}]*\\}", "");
        // Clean up double commas or leading comma after {
        result = result.replaceAll("\\{\\s*,", "{");
        result = result.replaceAll(",\\s*}", "}");
        return result;
    }

    /**
     * Returns the environment variable name that Claude Code will see.
     * Used by Main.java to set the variable on the process.
     */
    static String getEnvVarName() {
        return ENV_VAR;
    }
}