package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EnvironmentLoader {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentLoader.class);
    private static final String ENV_PREFIX = "env.";

    public static List<Environment> listEnvironments(Path envsDir) {
        List<Environment> result = new ArrayList<>();

        if (!Files.isDirectory(envsDir)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(envsDir, "*.properties")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - ".properties".length());
                Environment env = loadEnvironment(name, file);
                if (env != null) {
                    result.add(env);
                }
            }
        } catch (IOException e) {
            log.error("Error listing environments in {}: {}", envsDir, e.getMessage());
        }

        result.sort(Comparator.comparing(e -> e.name));
        return result;
    }

    static Environment loadEnvironment(String name, Path file) {
        try {
            String content = Files.readString(file);
            Properties properties = new Properties();
            properties.load(new StringReader(content));

            Map<String, String> raw = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                raw.put(key.trim().toLowerCase(Locale.ROOT), properties.getProperty(key));
            }

            Config config = new Config();
            config.https_proxy = Main.asString(raw.get("https_proxy"));
            config.http_proxy = Main.asString(raw.get("http_proxy"));
            config.no_proxy = Main.asString(raw.get("no_proxy"));
            config.disable_telemetry = Main.asBoolean(raw.get("disable_telemetry"));

            Map<String, String> extraEnvVars = new LinkedHashMap<>();
            for (String key : properties.stringPropertyNames()) {
                String trimmed = key.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith(ENV_PREFIX)) {
                    String envVarName = trimmed.substring(ENV_PREFIX.length());
                    String value = properties.getProperty(key);
                    if (value != null && !value.isBlank()) {
                        extraEnvVars.put(envVarName, value.trim());
                    }
                }
            }

            return new Environment(name, config, extraEnvVars);
        } catch (Exception e) {
            log.error("Error loading environment '{}' from {}: {}", name, file, e.getMessage());
            return null;
        }
    }
}
