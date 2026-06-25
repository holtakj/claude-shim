package com.proaut.claudeshim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the Claude shim.
 *
 * <p>The shim shadows the {@code claude} command on PATH, intercepts calls,
 * applies proxy/telemetry configuration, selects an environment, and delegates
 * to the real Claude binary.</p>
 */
public class Main {

    // Configure slf4j-simple before the logger is created (static fields init before main())
    static {
        System.setProperty("org.slf4j.simpleLogger.defaultFormat", "%message");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "false");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");
    }

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String MASKED = "*****";

    public static void main(String[] args) throws Exception {

        // Parse CLI arguments (--env, --version, --v)
        CliArgs cliArgs = CliArgs.from(args);

        // Handle --version / --v
        if (cliArgs.versionFlag()) {
            BuildInfo info = BuildInfo.load();
            if (info != null) {
                System.out.println(info);
            } else {
                System.out.println("claude-shim (build info not available)");
            }
            System.exit(0);
        }

        // Print version
        BuildInfo info = BuildInfo.load();
        if (info != null) {
            log.info("claude-shim {}", info.version() + "   Branch: " + info.branch() + "   Build date: " + info.date());
        }

        // Load global configuration
        Config cfg = loadConfig();

        // Resolve the environment to use
        Environment selectedEnv = resolveEnvironment(cliArgs.envName(), cfg);

        // Apply environment-level overrides to global config
        if (selectedEnv != null) {
            cfg = applyOverrides(cfg, selectedEnv.config());
        }

        // Locate the real Claude binary (skip the shim itself)
        String real = BinaryLocator.findRealClaude();
        if (real == null) {
            log.error("Claude Code binary not found on PATH. Is Claude Code installed?");
            System.exit(1);
        }
        log.info("Claude detected on path: {}", real);

        // Build the command to execute
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(real);
        cmd.addAll(cliArgs.forwardArgs());

        // Launch the process with configured environment variables
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();

        if (selectedEnv != null) {
            for (Map.Entry<String, String> entry : selectedEnv.extraEnvVars().entrySet()) {
                log.info("Setting env var from environment '{}': {}={}", selectedEnv.name(), entry.getKey(), maskCredentials(entry.getValue()));
                env.put(entry.getKey(), entry.getValue());
            }
        }

        if (cfg.https_proxy() != null) {
            log.info("Using HTTPS_PROXY: {}", maskCredentials(cfg.https_proxy()));
            env.put("HTTPS_PROXY", cfg.https_proxy());
        }
        if (cfg.http_proxy() != null) {
            log.info("Using HTTP_PROXY: {}", maskCredentials(cfg.http_proxy()));
            env.put("HTTP_PROXY", cfg.http_proxy());
        }
        if (cfg.no_proxy() != null) {
            log.info("Using NO_PROXY: {}", maskCredentials(cfg.no_proxy()));
            env.put("NO_PROXY", cfg.no_proxy());
        }
        if (Boolean.TRUE.equals(cfg.disable_telemetry())) {
            log.info("Telemetry disabled");
            env.put("DO_NOT_TRACK", "1");
            env.put("CLAUDE_DISABLE_TELEMETRY", "1");
        }

        if (cfg.theme() != null) {
            ClaudeSettings.applyTheme(cfg.theme());
        } else {
            ClaudeSettings.removeTheme();
        }

        // Print banner last, right before launching Claude
        if (selectedEnv != null) {
            String bannerColor = ClaudeSettings.readThemeColor(cfg.theme());
            Banner.print(selectedEnv.name(), bannerColor, info != null ? info.version() : null);
        }

        pb.inheritIO();
        Process p = pb.start();
        System.exit(p.waitFor());
    }

    /**
     * Masks credentials in proxy URLs (e.g. http://user:pass@host:port)
     * so sensitive information is never printed to the log.
     */
    static String maskCredentials(String url) {
        if (url == null) {
            return null;
        }
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) {
            return url;
        }
        // Find the last '@' after the scheme — handles @ in passwords.
        int atIdx = url.lastIndexOf('@', url.length() - 1);
        if (atIdx <= schemeIdx + 3) {
            return url;
        }
        // Replace everything between :// and @ with *****
        return url.substring(0, schemeIdx + 3)
                + MASKED
                + url.substring(atIdx);
    }

    // ---- config helpers ----

    static Config applyOverrides(Config base, Config override) {
        if (override == null) {
            return base;
        }
        return new Config(
                override.https_proxy() != null ? override.https_proxy() : base.https_proxy(),
                override.http_proxy() != null ? override.http_proxy() : base.http_proxy(),
                override.no_proxy() != null ? override.no_proxy() : base.no_proxy(),
                override.disable_telemetry() != null ? override.disable_telemetry() : base.disable_telemetry(),
                base.envPaths(),
                override.theme() != null ? override.theme() : base.theme()
        );
    }

    // ---- config loading ----

    private static Config loadConfig() {
        return loadConfig(defaultConfigPath());
    }

    static Config loadConfig(Path p) {
        log.info("Looking for config file at {}", p);

        if (!Files.exists(p)) {
            log.warn("No config file found at {}", p);
            return new Config();
        }

        Map<String, String> props = ConfigParser.parseContent(readFile(p));
        Config config = ConfigParser.parseGlobalConfig(props);

        // Merge path mappings into the default (mutable) envPaths
        Map<String, List<String>> pathMappings = ConfigParser.parsePathMappings(props);
        if (!pathMappings.isEmpty()) {
            // Create a mutable copy of the config's envPaths
            var mutableConfig = new com.proaut.claudeshim.Config(
                    config.https_proxy(),
                    config.http_proxy(),
                    config.no_proxy(),
                    config.disable_telemetry(),
                    new java.util.LinkedHashMap<>(config.envPaths()),
                    config.theme()
            );
            mutableConfig.envPaths().putAll(pathMappings);
            log.info("Loaded config from {}", p);
            return mutableConfig;
        }

        log.info("Loaded config from {}", p);
        return config;
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            log.error("Error reading {}: {}", path, e.getMessage());
            return "";
        }
    }

    // ---- environment resolution ----

    static Environment resolveEnvironment(String envName, Config cfg) {
        return EnvResolver.resolve(envName, envsDir(), cfg.envPaths(), EnvResolver.currentWorkingDirectory());
    }

    static Environment resolveEnvironment(String envName, Path envsDir) {
        return EnvResolver.resolve(envName, envsDir, Map.of(), EnvResolver.currentWorkingDirectory());
    }

    static Environment resolveEnvironment(String envName, Path envsDir,
                                          Map<String, List<String>> envPaths, Path cwd) {
        return EnvResolver.resolve(envName, envsDir, envPaths, cwd);
    }

    static String findEnvironmentByPath(Path cwd, Map<String, List<String>> envPaths) {
        return EnvResolver.findEnvironmentByPath(cwd, envPaths);
    }

    /**
     * Parse a properties string into a Config (used by tests).
     */
    static Config parseConfig(String content) {
        Map<String, String> props = ConfigParser.parseContent(content);
        Config config = ConfigParser.parseGlobalConfig(props);
        Map<String, List<String>> pathMappings = ConfigParser.parsePathMappings(props);
        if (pathMappings.isEmpty()) {
            return config;
        }
        var mutableConfig = new Config(
                config.https_proxy(),
                config.http_proxy(),
                config.no_proxy(),
                config.disable_telemetry(),
                new java.util.LinkedHashMap<>(config.envPaths()),
                config.theme()
        );
        mutableConfig.envPaths().putAll(pathMappings);
        return mutableConfig;
    }

    // ---- platform-specific config path ----

    private static Path defaultConfigPath() {
        return resolveDefaultConfigPath(
                System.getProperty("os.name", ""),
                System.getenv(),
                System.getProperty("user.home", ""));
    }

    static Path resolveDefaultConfigPath(String osName, Map<String, String> env, String userHome) {
        boolean windows = osName != null && osName.toLowerCase(java.util.Locale.ROOT).contains("windows");
        boolean mac = osName != null && osName.toLowerCase(java.util.Locale.ROOT).contains("mac");

        if (windows) {
            String appData = env.get("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "claude-shim", "config.properties");
            }
            return Path.of(userHome, "AppData", "Roaming", "claude-shim", "config.properties");
        }

        if (mac) {
            return Path.of(userHome, "Library", "Application Support", "claude-shim", "config.properties");
        }

        String xdgConfigHome = env.get("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            return Path.of(xdgConfigHome, "claude-shim", "config.properties");
        }

        return Path.of(userHome, ".config", "claude-shim", "config.properties");
    }

    private static Path envsDir() {
        return defaultConfigPath().getParent().resolve("envs");
    }
}
