package com.proaut.claudeshim;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a named environment with its configuration and extra environment variables.
 *
 * @param auth      authentication mode; never null
 * @param configDir Claude config directory for this environment ({@code CLAUDE_CONFIG_DIR});
 *                  always set in {@link AuthMode#LOGIN} mode, optional otherwise
 */
public record Environment(
        String name,
        Config config,
        Map<String, String> extraEnvVars,
        AuthMode auth,
        Path configDir
) {

    public Environment {
        extraEnvVars = extraEnvVars != null ? extraEnvVars : new LinkedHashMap<>();
        auth = auth != null ? auth : AuthMode.API_KEY;
    }

    public Environment(String name, Config config, Map<String, String> extraEnvVars) {
        this(name, config, extraEnvVars, AuthMode.API_KEY, null);
    }

    /** The directory Claude reads its settings and themes from for this environment. */
    Path claudeDir() {
        return configDir != null ? configDir : ClaudeSettings.defaultClaudeDir();
    }
}
