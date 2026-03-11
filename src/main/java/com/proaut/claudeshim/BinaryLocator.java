package com.proaut.claudeshim;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class BinaryLocator {

    public static String findRealClaude() {
        return findRealClaude(System.getenv("PATH"), currentCommandPath(), System.getProperty("os.name", ""));
    }

    static String findRealClaude(String path, Path selfPath) {
        return findRealClaude(path, selfPath, System.getProperty("os.name", ""));
    }

    static String findRealClaude(String path, Path selfPath, String osName) {

        if (path == null) return null;

        Path normalizedSelfPath = normalize(selfPath);
        boolean windows = isWindows(osName);

        for (String dir : path.split(File.pathSeparator)) {
            String trimmedDir = dir.trim();
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

    private static List<String> candidateNames(boolean windows) {
        if (windows) {
            return List.of("claude.exe", "claude.cmd", "claude.bat", "claude.com", "claude");
        }
        return List.of("claude");
    }

    private static boolean isRunnable(File candidate, boolean windows) {
        return candidate.exists() && (windows || candidate.canExecute());
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase().contains("windows");
    }

    private static Path currentCommandPath() {
        return ProcessHandle.current()
                .info()
                .command()
                .map(Paths::get)
                .orElse(null);
    }

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
