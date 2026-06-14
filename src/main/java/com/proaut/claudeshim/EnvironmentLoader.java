package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Loads named environments from {@code .properties} files in an environments directory.
 */
public final class EnvironmentLoader {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentLoader.class);

    private EnvironmentLoader() {}

    /**
     * List all environments found in the given directory.
     * Results are sorted alphabetically by environment name.
     */
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

        result.sort(Comparator.comparing(Environment::name));
        return result;
    }

    /**
     * Load a single environment from a properties file.
     */
    static Environment loadEnvironment(String name, Path file) {
        try {
            Map<String, String> props = ConfigParser.parseContent(readFile(file));
            Config config = ConfigParser.parseGlobalConfig(props);
            Map<String, String> extraEnvVars = ConfigParser.parseExtraEnvVars(props);
            return new Environment(name, config, extraEnvVars);
        } catch (Exception e) {
            log.error("Error loading environment '{}' from {}: {}", name, file, e.getMessage());
            return null;
        }
    }

    private static String readFile(Path path) throws IOException {
        return Files.readString(path);
    }
}
