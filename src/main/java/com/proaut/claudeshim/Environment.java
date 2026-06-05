package com.proaut.claudeshim;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an environment configuration for the Claude Shim.
 * <p>
 * An environment consists of a name, proxy/telemetry configuration, and a set of
 * additional environment variables that should be set when using this environment.
 */
public record Environment(String name, ProxyConfig config, Map<String, String> extraEnvVars) {

    /**
     * Creates a new environment with the specified parameters.
     *
     * @param name         the unique name of this environment
     * @param config       the proxy and telemetry configuration
     * @param extraEnvVars additional environment variables to set (can be empty)
     */
    public Environment {
        // Validate that name is not null or blank
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Environment name cannot be null or blank");
        }
        // Ensure extraEnvVars is never null
        if (extraEnvVars == null) {
            extraEnvVars = new LinkedHashMap<>();
        } else {
            // Create a defensive copy to ensure immutability
            extraEnvVars = new LinkedHashMap<>(extraEnvVars);
        }
    }

    /**
     * Creates a new environment with empty extra environment variables.
     *
     * @param name   the unique name of this environment
     * @param config the proxy and telemetry configuration
     */
    public Environment(String name, ProxyConfig config) {
        this(name, config, new LinkedHashMap<>());
    }

    @Override
    public String toString() {
        return "Environment{" +
            "name='" + name + '\'' +
            ", config=" + config +
            ", extraEnvVars=" + extraEnvVars +
            '}';
    }
}
