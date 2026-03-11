package com.proaut.claudeshim;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BinaryLocator {

    public static String findRealClaude() {
        return findRealClaude(System.getenv("PATH"), currentCommandPath());
    }

    static String findRealClaude(String path, Path selfPath) {

        if (path == null) return null;

        Path normalizedSelfPath = normalize(selfPath);

        for (String dir : path.split(File.pathSeparator)) {

            File f = new File(dir.trim(), "claude");
            Path candidatePath = normalize(f.toPath());

            if (normalizedSelfPath != null && normalizedSelfPath.equals(candidatePath))
                continue;

            if (f.exists() && f.canExecute())
                return f.getAbsolutePath();
        }

        return null;
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
