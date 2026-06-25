package com.proaut.claudeshim;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Finds the real Claude binary on PATH, skipping the shim itself.
 *
 * <p>The shim shadows the {@code claude} command on PATH. When invoked,
 * this class scans PATH to find the real Claude binary (the shim skips itself).</p>
 */
public final class BinaryLocator {

    private static final Logger log = LoggerFactory.getLogger(BinaryLocator.class);

    private BinaryLocator() {}

    /**
     * Locate the real Claude binary on PATH.
     */
    public static String findRealClaude() {
        Path currentCommandPath = currentCommandPath();
        log.info("Claude-Shim command path: {}", currentCommandPath);
        String realClaudePath = findRealClaude(
                System.getenv("PATH"),
                currentCommandPath,
                System.getProperty("os.name", ""));
        log.info("Real Claude binary path: {}", realClaudePath);
        return realClaudePath;
    }

    /**
     * Locate the real Claude binary on PATH (overridable for testing).
     */
    static String findRealClaude(String path, Path selfPath) {
        return findRealClaude(path, selfPath, System.getProperty("os.name", ""));
    }

    static String findRealClaude(String path, Path selfPath, String osName) {
        if (StringUtils.isBlank(path)) {
            return null;
        }

        Path normalizedSelfPath = normalize(selfPath);
        log.info("Normalized self path: {}", normalizedSelfPath);

        boolean windows = isWindows(osName);

        if (windows) {
            log.info("Running on Windows, will look for .exe, .cmd, .bat, .com extensions");
        }

        for (String dir : path.split(File.pathSeparator)) {
            String trimmedDir = StringUtils.trim(dir);
            if (trimmedDir.isEmpty()) {
                continue;
            }

            for (String executableName : candidateNames(windows)) {
                File f = new File(trimmedDir, executableName);
                Path candidatePath = normalize(f.toPath());

                if (normalizedSelfPath != null && normalizedSelfPath.equals(candidatePath)) {
                    continue;
                }

                if (isRunnable(f, windows)) {
                    return f.getAbsolutePath();
                }
            }
        }

        return null;
    }

    /**
     * Platform-specific candidate executable names.
     */
    private static List<String> candidateNames(boolean windows) {
        if (windows) {
            return List.of("claude.exe", "claude.cmd", "claude.bat", "claude.com", "claude");
        }
        return List.of("claude");
    }

    /**
     * Check if a file is an executable binary.
     */
    private static boolean isRunnable(File candidate, boolean windows) {
        return candidate.exists() && (windows || candidate.canExecute());
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(java.util.Locale.ROOT).contains("windows");
    }

    /**
     * Resolve the current process's own executable path.
     */
    private static Path currentCommandPath() {
        return ProcessHandle.current()
                .info()
                .commandLine()
                .map(cmdLine -> cmdLine.split("\\s+")[0])
                .map(Paths::get)
                .orElse(null);
    }

    /**
     * Normalize a path to its real absolute form.
     */
    private static Path normalize(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return path.toRealPath();
        } catch (IOException ignored) {
            return path.toAbsolutePath().normalize();
        }
    }
}
