package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.nio.file.*;
import java.util.*;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        Config cfg = loadConfig();

        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");


        String real = BinaryLocator.findRealClaude();

        log.info("Claude detected on path: {}", real);

        List<String> cmd = new ArrayList<>();
        cmd.add(real);
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);

        Map<String, String> env = pb.environment();

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

    private static Config loadConfig() {
        return loadConfig(defaultConfigPath());
    }

    static Config loadConfig(Path p) {
        try {
            log.info("Looking for config file at {}", p);

            if (!Files.exists(p)) {
                Path legacyYaml = p.resolveSibling("config.yaml");
                if (Files.exists(legacyYaml)) {
                    log.warn("Found legacy YAML config at {} but only Java properties are supported now; migrate it to {}", legacyYaml, p);
                } else {
                    log.warn("No config file found at {}", p);
                }
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
        return Paths.get(System.getProperty("user.home"), ".config", "claude-shim", "config.properties");
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Boolean asBoolean(Object value) {
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
