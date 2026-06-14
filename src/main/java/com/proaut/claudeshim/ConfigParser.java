package com.proaut.claudeshim;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Shared utilities for parsing .properties configuration files.
 * Used by {@link Main} and {@link EnvironmentLoader}.
 */
final class ConfigParser {

    private static final Logger log = LoggerFactory.getLogger(ConfigParser.class);

    private static final String ENV_PREFIX = "env.";
    private static final String PATHS_PREFIX = "paths.";

    private ConfigParser() {}

    // ---- file loading ----

    /**
     * Load a properties file and return the content as a case-preserving map.
     * The returned map preserves the original key casing from the file.
     * Global config lookups use case-insensitive matching.
     */
    static Map<String, String> parseContent(String content) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(content));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid properties configuration", e);
        }

        Map<String, String> result = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name.trim(), properties.getProperty(name));
        }
        return result;
    }

    // ---- global config parsing ----

    /**
     * Parse global config keys from a properties map.
     * Uses case-insensitive key lookup.
     */
    static Config parseGlobalConfig(Map<String, String> props) {
        return new Config(
                lookup(props, "https_proxy"),
                lookup(props, "http_proxy"),
                lookup(props, "no_proxy"),
                lookupBool(props, "disable_telemetry"),
                new java.util.LinkedHashMap<>()
        );
    }

    /**
     * Case-insensitive lookup for a global config string value.
     */
    private static String lookup(Map<String, String> props, String key) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (entry.getKey().trim().toLowerCase(Locale.ROOT).equals(lowerKey)) {
                return asString(entry.getValue());
            }
        }
        return null;
    }

    /**
     * Case-insensitive lookup for a global config boolean value.
     */
    private static Boolean lookupBool(Map<String, String> props, String key) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (entry.getKey().trim().toLowerCase(Locale.ROOT).equals(lowerKey)) {
                return asBoolean(entry.getValue());
            }
        }
        return null;
    }

    // ---- environment-specific parsing ----

    /**
     * Parse extra environment variables (keys starting with "env.").
     * The key after "env." preserves its original casing from the file.
     */
    static Map<String, String> parseExtraEnvVars(Map<String, String> props) {
        Map<String, String> extra = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey().trim();
            if (key.toLowerCase(Locale.ROOT).startsWith(ENV_PREFIX)) {
                String envVarName = key.substring(ENV_PREFIX.length());
                if (StringUtils.isNotBlank(entry.getValue())) {
                    extra.put(envVarName, StringUtils.trim(entry.getValue()));
                }
            }
        }
        return extra;
    }

    // ---- path mapping parsing ----

    /**
     * Parse "paths.<name>" entries from a properties map into a path-mapping table.
     * Uses case-insensitive prefix matching.
     */
    static Map<String, List<String>> parsePathMappings(Map<String, String> props) {
        Map<String, List<String>> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey().trim();
            if (key.toLowerCase(Locale.ROOT).startsWith(PATHS_PREFIX)) {
                String envName = key.substring(PATHS_PREFIX.length());
                if (envName.isEmpty()) {
                    continue;
                }
                String rawPaths = entry.getValue();
                if (rawPaths == null || rawPaths.isBlank()) {
                    continue;
                }
                List<String> paths = new ArrayList<>();
                for (String part : rawPaths.split(",")) {
                    String trimmedPart = StringUtils.trim(part);
                    if (!trimmedPart.isEmpty()) {
                        paths.add(trimmedPart);
                    }
                }
                if (!paths.isEmpty()) {
                    mappings.put(envName, paths);
                }
            }
        }
        return mappings;
    }

    // ---- helpers ----

    static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = value.toString().trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "1", "true", "yes", "on" -> Boolean.TRUE;
            case "0", "false", "no", "off" -> Boolean.FALSE;
            default -> null;
        };
    }
}
