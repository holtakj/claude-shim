package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");

        Config cfg = loadConfig();

        String envName = null;
        List<String> forwardArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--env".equals(args[i]) && i + 1 < args.length) {
                envName = args[++i];
            } else {
                forwardArgs.add(args[i]);
            }
        }

        Environment selectedEnv = resolveEnvironment(envName);

        if (selectedEnv != null) {
            log.info("Using environment: {}", selectedEnv.name);
            if (selectedEnv.config.https_proxy != null) cfg.https_proxy = selectedEnv.config.https_proxy;
            if (selectedEnv.config.http_proxy != null) cfg.http_proxy = selectedEnv.config.http_proxy;
            if (selectedEnv.config.no_proxy != null) cfg.no_proxy = selectedEnv.config.no_proxy;
            if (selectedEnv.config.disable_telemetry != null) cfg.disable_telemetry = selectedEnv.config.disable_telemetry;
        }

        String real = BinaryLocator.findRealClaude();

        log.info("Claude detected on path: {}", real);

        List<String> cmd = new ArrayList<>();
        cmd.add(real);
        cmd.addAll(forwardArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);

        Map<String, String> env = pb.environment();

        if (selectedEnv != null) {
            for (Map.Entry<String, String> entry : selectedEnv.extraEnvVars.entrySet()) {
                log.info("Setting env var from environment '{}': {}", selectedEnv.name, entry.getKey());
                env.put(entry.getKey(), entry.getValue());
            }
        }

        if (cfg.https_proxy != null) {
            log.info("Using HTTPS_PROXY: {}", cfg.https_proxy);
            env.put("HTTPS_PROXY", cfg.https_proxy);
        }

        if (cfg.http_proxy != null) {
            log.info("Using HTTP_PROXY: {}", cfg.http_proxy);
            env.put("HTTP_PROXY", cfg.http_proxy);
        }

        if (cfg.no_proxy != null) {
            log.info("Using NO_PROXY: {}", cfg.no_proxy);
            env.put("NO_PROXY", cfg.no_proxy);
        }

        if (Boolean.TRUE.equals(cfg.disable_telemetry)) {
            log.info("Telemetry disabled");
            env.put("DO_NOT_TRACK", "1");
            env.put("CLAUDE_DISABLE_TELEMETRY", "1");
        }

        pb.inheritIO();

        Process p = pb.start();
        System.exit(p.waitFor());
    }

    static Environment resolveEnvironment(String envName) {
        return resolveEnvironment(envName, envsDir());
    }

    static Environment resolveEnvironment(String envName, Path envsDir) {
        log.info("Looking for environments in {}", envsDir);
        List<Environment> environments = EnvironmentLoader.listEnvironments(envsDir);

        if (environments.isEmpty()) {
            return null;
        }

        if (envName != null) {
            for (Environment e : environments) {
                if (e.name.equals(envName)) {
                    return e;
                }
            }
            log.error("Environment '{}' not found. Available: {}", envName,
                    environments.stream().map(e -> e.name).toList());
            System.exit(1);
        }

        if (environments.size() == 1) {
            log.info("Only one environment found: {}. Using it.", environments.get(0).name);
            return environments.get(0);
        }

        return promptForEnvironment(environments);
    }

    private static Environment promptForEnvironment(List<Environment> environments) {
        System.err.println("Select environment:");
        for (int i = 0; i < environments.size(); i++) {
            System.err.printf("  [%d] %s%n", i + 1, environments.get(i).name);
        }
        System.err.print("Choice: ");
        System.err.flush();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
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
            return null;
        }
    }

    private static Path envsDir() {
        return defaultConfigPath().getParent().resolve("envs");
    }

    private static Config loadConfig() {
        return loadConfig(defaultConfigPath());
    }

    static Config loadConfig(Path p) {
        try {
            log.info("Looking for config file at {}", p);

            if (!Files.exists(p)) {
                log.warn("No config file found at {}", p);
                return new Config();
            }

            String content = Files.readString(p);
            Config c = parseConfig(content);
            log.info("Loaded config from {}", p);
            return c;

        } catch (Exception e) {
            log.error("Error loading config: {}", e.getMessage());
        }

        return new Config();
    }

    static Config parseConfig(String content) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(content));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid properties configuration", e);
        }

        Map<String, String> parsed = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            parsed.put(name.trim().toLowerCase(Locale.ROOT), properties.getProperty(name));
        }

        Config c = new Config();
        c.https_proxy = asString(parsed.get("https_proxy"));
        c.http_proxy = asString(parsed.get("http_proxy"));
        c.no_proxy = asString(parsed.get("no_proxy"));
        c.disable_telemetry = asBoolean(parsed.get("disable_telemetry"));
        return c;
    }

    private static Path defaultConfigPath() {
        return resolveDefaultConfigPath(System.getProperty("os.name", ""), System.getenv(), System.getProperty("user.home", ""));
    }

    static Path resolveDefaultConfigPath(String osName, Map<String, String> env, String userHome) {
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
