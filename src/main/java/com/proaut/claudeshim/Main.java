package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main entry point for the Claude Shim - a native-friendly wrapper for the claude CLI.
 * <p>
 * Features:
 * <ul>
 *   <li>PATH shadowing (no renaming required)</li>
 *   <li>HTTP/HTTPS proxy support</li>
 *   <li>Environment-based configuration</li>
 *   <li>GraalVM native binary support</li>
 * </ul>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String ENV_PREFIX = "env.";

    // ==================== Data Records ====================

    /**
     * Holds parsed command-line arguments.
     */
    public record ParsedArgs(String envName, boolean forceMenu, List<String> forwardArgs) {}

    /**
     * Holds environment-specific configuration including extra environment variables.
     */
    public record EnvironmentConfig(String name, ProxyConfig proxyConfig, Map<String, String> extraEnvVars) {}

    // ==================== Main Entry Point ====================

    public static void main(String[] args) throws Exception {
        configureLogging();

        Config config = loadConfig(defaultConfigPath());
        ParsedArgs parsedArgs = parseArgs(args);

        Environment selectedEnv = resolveEnvironment(parsedArgs, config);

        if (selectedEnv != null) {
            log.info("Using environment: {}", selectedEnv.name());
            config = applyEnvironmentConfig(config, selectedEnv);
        } else {
            log.warn("No environment specified, using default configuration");
        }

        String realClaudePath = BinaryLocator.findRealClaude();
        log.info("Claude detected on path: {}", realClaudePath);

        List<String> command = new ArrayList<>();
        command.add(realClaudePath);
        command.addAll(parsedArgs.forwardArgs());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> environment = processBuilder.environment();

        if (selectedEnv != null) {
            selectedEnv.extraEnvVars().forEach((key, value) -> {
                log.info("Setting env var from environment '{}': {}", selectedEnv.name(), key);
                environment.put(key, value);
            });
        }

        applyProxyConfig(environment, config.proxy());
        applyTelemetryConfig(environment, config.proxy());

        processBuilder.inheritIO();

        Process process = processBuilder.start();
        System.exit(process.waitFor());
    }

    // ==================== Configuration ====================

    private static void configureLogging() {
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");
    }

    public static Config loadConfig(Path configPath) {
        try {
            log.info("Looking for config file at {}", configPath);

            if (!Files.exists(configPath)) {
                log.warn("No config file found at {}", configPath);
                return new Config(ProxyConfig.empty(), Map.of());
            }

            String content = Files.readString(configPath);
            Config loadedConfig = parseConfig(content);
            log.info("Loaded config from {}", configPath);
            return loadedConfig;

        } catch (IOException e) {
            log.error("Error loading config: {}", e.getMessage());
            return new Config(ProxyConfig.empty(), Map.of());
        }
    }

    public static Config parseConfig(String content) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(content));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid properties configuration", e);
        }

        Map<String, String> normalized = normalizeKeys(properties);

        ProxyConfig proxyConfig = buildProxyConfig(normalized);
        Map<String, List<String>> pathMappings = buildPathMappings(normalized);

        return new Config(proxyConfig, pathMappings);
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
            asString(config.get("https_proxy")),
            asString(config.get("http_proxy")),
            asString(config.get("no_proxy")),
            asBoolean(config.get("disable_telemetry"))
        );
    }

    private static Map<String, List<String>> buildPathMappings(Map<String, String> config) {
        return config.entrySet().stream()
            .filter(e -> e.getKey().startsWith("paths."))
            .map(e -> {
                String envName = e.getKey().substring("paths.".length());
                String value = e.getValue();
                return envName.isEmpty() ? null : Map.entry(envName, parsePathList(value));
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a
            ));
    }

    private static List<String> parsePathList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .toList();
    }

    // ==================== Environment Resolution ====================

    public static Environment resolveEnvironment(ParsedArgs args, Config config) {
        Path envsDir = defaultConfigPath().getParent().resolve("envs");
        Path cwd = currentWorkingDirectory();
        return resolveEnvironment(args.envName(), args.forceMenu(), envsDir, config.pathMappings(), cwd);
    }

    public static Environment resolveEnvironment(String envName, Path envsDir) {
        return resolveEnvironment(envName, false, envsDir, Map.of(), currentWorkingDirectory());
    }

    public static Environment resolveEnvironment(String envName, Path envsDir, Map<String, List<String>> envPaths, Path cwd) {
        return resolveEnvironment(envName, false, envsDir, envPaths, cwd);
    }

    public static Environment resolveEnvironment(String envName, boolean forceMenu, Path envsDir,
                                                 Map<String, List<String>> pathMappings, Path cwd) {
        log.info("Looking for environments in {}", envsDir);
        log.info("Current working directory: {}", cwd);

        List<Environment> environments = loadEnvironments(envsDir);
        log.info("Found {} environment(s) in {}", environments.size(), envsDir);

        if (environments.isEmpty()) {
            return null;
        }

        if (envName != null) {
            return findEnvironmentByName(environments, envName);
        }

        if (forceMenu) {
            log.info("Empty --env provided, prompting for environment");
            return promptForEnvironment(environments);
        }

        String pathMatch = PathMapper.findMatchingEnvironment(cwd, pathMappings);
        if (pathMatch != null) {
            Environment env = findEnvironmentByName(environments, pathMatch, false);
            if (env != null) {
                log.info("Auto-selected environment '{}' from working directory {}", env.name(), cwd);
                return env;
            }
            log.warn("Path mapping points to environment '{}', but no such environment file exists. " +
                    "Falling back to default selection.", pathMatch);
        } else {
            log.info("No environment matched by path for working directory {}", cwd);
        }

        if (environments.size() == 1) {
            log.info("Only one environment found: {}. Using it.", environments.get(0).name());
            return environments.get(0);
        }

        return promptForEnvironment(environments);
    }

    private static List<Environment> loadEnvironments(Path envsDir) {
        try {
            return EnvironmentLoader.listEnvironments(envsDir);
        } catch (Exception e) {
            log.error("Error loading environments: {}", e.getMessage());
            return List.of();
        }
    }

    private static Environment findEnvironmentByName(List<Environment> environments, String name, boolean exitOnError) {
        Environment result = environments.stream()
            .filter(e -> e.name().equals(name))
            .findFirst()
            .orElse(null);

        if (result == null && exitOnError) {
            String available = environments.stream()
                .map(Environment::name)
                .collect(Collectors.joining(", "));
            log.error("Environment '{}' not found. Available: {}", name, available);
            System.exit(1);
        }
        return result;
    }

    private static Environment findEnvironmentByName(List<Environment> environments, String name) {
        return findEnvironmentByName(environments, name, true);
    }

    private static Environment promptForEnvironment(List<Environment> environments) {
        try {
            log.info("Prompting for environment");
            return InteractivePrompt.select("Select environment:", environments, Environment::name);
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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
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
        } catch (IOException | NumberFormatException e) {
            System.err.println("Invalid input.");
            System.exit(1);
            return null;
        }
    }

    private static Config applyEnvironmentConfig(Config config, Environment env) {
        ProxyConfig mergedProxy = mergeProxyConfig(config.proxy(), env.config());
        return new Config(mergedProxy, config.pathMappings());
    }

    private static ProxyConfig mergeProxyConfig(ProxyConfig base, ProxyConfig overlay) {
        return new ProxyConfig(
            overlay.httpsProxy() != null ? overlay.httpsProxy() : base.httpsProxy(),
            overlay.httpProxy() != null ? overlay.httpProxy() : base.httpProxy(),
            overlay.noProxy() != null ? overlay.noProxy() : base.noProxy(),
            overlay.disableTelemetry() != null ? overlay.disableTelemetry() : base.disableTelemetry()
        );
    }

    private static void applyProxyConfig(Map<String, String> env, ProxyConfig proxy) {
        if (proxy.httpsProxy() != null) {
            log.info("Using HTTPS_PROXY: {}", maskPasswordInUrl(proxy.httpsProxy()));
            env.put("HTTPS_PROXY", proxy.httpsProxy());
        }
        if (proxy.httpProxy() != null) {
            log.info("Using HTTP_PROXY: {}", maskPasswordInUrl(proxy.httpProxy()));
            env.put("HTTP_PROXY", proxy.httpProxy());
        }
        if (proxy.noProxy() != null) {
            log.info("Using NO_PROXY: {}", proxy.noProxy());
            env.put("NO_PROXY", proxy.noProxy());
        }
    }

    private static void applyTelemetryConfig(Map<String, String> env, ProxyConfig proxy) {
        if (Boolean.TRUE.equals(proxy.disableTelemetry())) {
            log.info("Telemetry disabled");
            env.put("DO_NOT_TRACK", "1");
            env.put("CLAUDE_DISABLE_TELEMETRY", "1");
        }
    }

    // ==================== Argument Parsing ====================

    public static ParsedArgs parseArgs(String[] args) {
        String envName = null;
        boolean forceMenu = false;
        List<String> forwardArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--env".equals(arg)) {
                // Handle --env [value] or --env (bare for menu)
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    envName = args[++i];
                }
                if (envName == null || envName.isBlank()) {
                    envName = null;
                    forceMenu = true;
                }
            } else if (arg.startsWith("--env=")) {
                // Handle --env=value syntax
                String value = arg.substring("--env=".length());
                if (value.isBlank()) {
                    forceMenu = true;
                } else {
                    envName = value;
                }
            } else {
                forwardArgs.add(arg);
            }
        }

        return new ParsedArgs(envName, forceMenu, forwardArgs);
    }

    // ==================== Utility Methods ====================

    private static Path currentWorkingDirectory() {
        try {
            return Paths.get("").toAbsolutePath();
        } catch (Exception e) {
            log.warn("Could not determine working directory: {}", e.getMessage());
            return null;
        }
    }

    private static Path defaultConfigPath() {
        String osName = System.getProperty("os.name", "");
        Map<String, String> env = System.getenv();
        String userHome = System.getProperty("user.home", "");
        return resolveDefaultConfigPath(osName, env, userHome);
    }

    public static Path resolveDefaultConfigPath(String osName, Map<String, String> env, String userHome) {
        boolean windows = osName != null && osName.toLowerCase(Locale.ROOT).contains("windows");
        boolean mac = osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");

        if (windows) {
            String appData = env.get("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, "claude-shim", "config.properties");
            }
            return Paths.get(userHome, "AppData", "Roaming", "claude-shim", "config.properties");
        }

        if (mac) {
            return Paths.get(userHome, "Library", "Application Support", "claude-shim", "config.properties");
        }

        String xdgConfigHome = env.get("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            return Paths.get(xdgConfigHome, "claude-shim", "config.properties");
        }

        return Paths.get(userHome, ".config", "claude-shim", "config.properties");
    }

    private static String maskPasswordInUrl(String url) {
        if (url == null) return null;
        int protoIdx = url.indexOf("://");
        if (protoIdx != -1) {
            int startUserinfo = protoIdx + 3;
            int atIdx = url.indexOf('@', startUserinfo);
            if (atIdx != -1) {
                String userinfo = url.substring(startUserinfo, atIdx);
                int colonIdx = userinfo.indexOf(':');
                if (colonIdx != -1 && colonIdx < userinfo.length() - 1) {
                    String username = userinfo.substring(0, colonIdx);
                    return url.substring(0, startUserinfo) + username + ":*****" + url.substring(atIdx);
                }
            }
        }
        return url;
    }

    public static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static Boolean asBoolean(Object value) {
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

    // ==================== Config Class ====================

    /**
     * Holds the parsed configuration including proxy settings and path mappings.
     */
    public record Config(ProxyConfig proxy, Map<String, List<String>> pathMappings) {}

    // ==================== Logger Access ====================

    /**
     * Returns the logger instance for this class.
     * <p>
     * This method is provided for utility classes that need to log but don't have
     * direct access to the private {@code log} field.
     *
     * @return the logger instance
     */
    static Logger log() {
        return log;
    }
}
