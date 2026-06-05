package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for loading environment configurations from properties files.
 * <p>
 * Environment files are stored in the {@code envs/} subdirectory of the config location
 * and contain configuration overrides plus arbitrary environment variables.
 */
public final class EnvironmentLoader {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentLoader.class);
    private static final String ENV_PREFIX = "env.";

    private EnvironmentLoader() {}

    /**
     * Lists all environments found in the given directory.
     * <p>
     * Scans the directory for {@code *.properties} files and loads each as an environment.
     * The resulting list is sorted alphabetically by environment name.
     *
     * @param envsDir the directory containing environment property files
     * @return a sorted list of environments found in the directory
     */
    public static List<Environment> listEnvironments(Path envsDir) {
        if (!Files.isDirectory(envsDir)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(envsDir, "*.properties")) {
            List<Environment> environments = new ArrayList<>();

            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - ".properties".length());
                Environment env = loadEnvironment(name, file);
                if (env != null) {
                    environments.add(env);
                }
            }

            environments.sort(Comparator.comparing(e -> e.name()));
            return environments;
        } catch (IOException e) {
            log.error("Error listing environments in {}: {}", envsDir, e.getMessage());
            return List.of();
        }
    }

    /**
     * Loads an environment from a property file.
     *
     * @param name  the name of the environment (derived from the filename)
     * @param file  the path to the properties file
     * @return the loaded environment, or null if an error occurs
     */
    static Environment loadEnvironment(String name, Path file) {
        try {
            String content = Files.readString(file);
            Properties properties = new Properties();
            properties.load(new StringReader(content));

            Map<String, String> raw = normalizeKeys(properties);

            ProxyConfig proxyConfig = buildProxyConfig(raw);
            Map<String, String> extraEnvVars = buildExtraEnvVars(properties, raw);

            return new Environment(name, proxyConfig, extraEnvVars);
        } catch (IOException e) {
            log.error("Error loading environment '{}' from {}: {}", name, file, e.getMessage());
            return null;
        }
    }

    private static Map<String, String> normalizeKeys(Properties properties) {
        return properties.stringPropertyNames().stream()
            .collect(Collectors.toMap(
                key -> key.trim().toLowerCase(Locale.ROOT),
                properties::getProperty
            ));
    }

    private static ProxyConfig buildProxyConfig(Map<String, String> config) {
        return new ProxyConfig(
            Main.asString(config.get("https_proxy")),
            Main.asString(config.get("http_proxy")),
            Main.asString(config.get("no_proxy")),
            Main.asBoolean(config.get("disable_telemetry"))
        );
    }

    private static Map<String, String> buildExtraEnvVars(Properties properties, Map<String, String> normalized) {
        return properties.stringPropertyNames().stream()
            .filter(key -> key.trim().toLowerCase(Locale.ROOT).startsWith(ENV_PREFIX))
            .map(key -> {
                String envVarName = key.trim().substring(ENV_PREFIX.length());
                String value = properties.getProperty(key);
                return envVarName.isEmpty() || value == null || value.isBlank() ? null
                    : Map.entry(envVarName, value.trim());
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a
            ));
    }
}
