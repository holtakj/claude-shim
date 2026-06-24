package com.proaut.claudeshim;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Resolves which environment to use based on CLI flags, path mappings, and user input.
 */
final class EnvResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvResolver.class);

    private EnvResolver() {}

    /**
     * Resolve the environment to use, following this priority:
     * <ol>
     *   <li>Explicit {@code --env} flag</li>
     *   <li>Path-based auto-selection (longest match wins)</li>
     *   <li>Single environment auto-selection</li>
     *   <li>Interactive prompt (if multiple environments)</li>
     * </ol>
     */
    static Environment resolve(String envName, Path envsDir,
                               Map<String, List<String>> envPaths, Path cwd) {
        log.info("Looking for environments in {}", envsDir);
        List<Environment> environments = EnvironmentLoader.listEnvironments(envsDir);

        if (environments.isEmpty()) {
            return null;
        }

        // 1. Explicit --env flag
        if (envName != null) {
            for (Environment e : environments) {
                if (e.name().equals(envName)) {
                    return e;
                }
            }
            log.error("Environment '{}' not found. Available: {}", envName,
                    environments.stream().map(Environment::name).toList());
            System.exit(1);
            return null; // unreachable, but needed for compilation
        }

        // 2. Path-based auto-selection
        String pathMatchedEnv = findEnvironmentByPath(cwd, envPaths);
        if (pathMatchedEnv != null) {
            for (Environment e : environments) {
                if (e.name().equals(pathMatchedEnv)) {
                    log.info("Auto-selected environment '{}' from working directory {}", e.name(), cwd);
                    return e;
                }
            }
            log.warn("Path mapping points to environment '{}', but no such environment file exists. " +
                    "Falling back to default selection.", pathMatchedEnv);
        }

        // 3. Single environment
        if (environments.size() == 1) {
            log.info("Only one environment found: {}. Using it.", environments.getFirst().name());
            return environments.getFirst();
        }

        // 4. Interactive prompt
        return promptForEnvironment(environments);
    }

    // ---- path matching ----

    static String findEnvironmentByPath(Path cwd, Map<String, List<String>> envPaths) {
        if (cwd == null || envPaths == null || envPaths.isEmpty()) {
            return null;
        }

        Path normalizedCwd;
        try {
            normalizedCwd = cwd.toAbsolutePath().normalize();
        } catch (Exception e) {
            log.warn("Could not normalize working directory '{}': {}", cwd, e.getMessage());
            return null;
        }

        String bestMatch = null;
        int bestDepth = -1;

        for (Map.Entry<String, List<String>> entry : envPaths.entrySet()) {
            for (String pathStr : entry.getValue()) {
                Path mappedPath = expandAndNormalize(pathStr);
                if (mappedPath == null) {
                    continue;
                }
                if (normalizedCwd.startsWith(mappedPath)) {
                    int depth = mappedPath.getNameCount();
                    if (depth > bestDepth) {
                        bestDepth = depth;
                        bestMatch = entry.getKey();
                    }
                }
            }
        }
        return bestMatch;
    }

    static Path expandAndNormalize(String pathStr) {
        if (StringUtils.isBlank(pathStr)) {
            return null;
        }
        try {
            String expanded = pathStr;
            if (expanded.equals("~") || expanded.startsWith("~/") || expanded.startsWith("~\\")) {
                String home = System.getProperty("user.home", "");
                if (!home.isEmpty()) {
                    expanded = home + expanded.substring(1);
                }
            }
            return Paths.get(expanded).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.warn("Invalid path '{}' in path mappings: {}", pathStr, e.getMessage());
            return null;
        }
    }

    static Path currentWorkingDirectory() {
        try {
            return Paths.get("").toAbsolutePath();
        } catch (Exception e) {
            log.warn("Could not determine working directory: {}", e.getMessage());
            return null;
        }
    }

    // ---- prompting ----

    private static Environment promptForEnvironment(List<Environment> environments) {
        try {
            return InteractivePrompt.select("Select environment:", environments, Environment::name,
                    env -> ClaudeSettings.readThemeColor(env.config().theme()));
        } catch (Exception e) {
            log.debug("Interactive prompt unavailable ({}), falling back to numeric input", e.getMessage());
            return promptForEnvironmentNumeric(environments);
        }
    }

    private static Environment promptForEnvironmentNumeric(List<Environment> environments) {
        System.err.println("Select environment:");
        for (int i = 0; i < environments.size(); i++) {
            System.err.printf("  [%d] %s%n", i + 1, environments.get(i).name());
        }
        System.err.print("Choice: ");
        System.err.flush();

        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in));
            String line = reader.readLine();
            if (line == null) {
                System.exit(1);
            }
            int choice = Integer.parseInt(line.trim());
            if (choice < 1 || choice > environments.size()) {
                System.err.println("Invalid choice.");
                System.exit(1);
            }
            return environments.get(choice - 1);
        } catch (Exception e) {
            System.err.println("Invalid input.");
            System.exit(1);
            return null; // unreachable
        }
    }
}
