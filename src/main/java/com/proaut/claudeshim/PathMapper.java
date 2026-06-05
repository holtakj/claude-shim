package com.proaut.claudeshim;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Utility class for mapping working directories to environment names.
 * <p>
 * This class provides functionality to find the appropriate environment based on
 * the current working directory by matching against configured path mappings.
 */
public final class PathMapper {

    private PathMapper() {}

    /**
     * Finds the environment name that matches the current working directory.
     * <p>
     * The matching uses the "longest path wins" strategy - if multiple path mappings
     * match the current directory, the one with the deepest nesting level is selected.
     *
     * @param cwd the current working directory
     * @param pathMappings a map of environment names to their configured paths
     * @return the name of the matching environment, or null if no match is found
     */
    public static String findMatchingEnvironment(Path cwd, List<String> pathMappings) {
        if (cwd == null || pathMappings == null || pathMappings.isEmpty()) {
            return null;
        }

        Path normalizedCwd = normalizePath(cwd);
        if (normalizedCwd == null) {
            return null;
        }

        String bestMatch = null;
        int bestDepth = -1;

        for (String pathStr : pathMappings) {
            Path mappedPath = expandAndNormalize(pathStr);
            if (mappedPath == null) {
                continue;
            }

            if (normalizedCwd.startsWith(mappedPath)) {
                int depth = mappedPath.getNameCount();
                if (depth > bestDepth) {
                    bestDepth = depth;
                    bestMatch = pathStr;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Finds the environment name by matching against a map of environment names to path lists.
     *
     * @param cwd the current working directory
     * @param envPaths a map where keys are environment names and values are lists of paths
     * @return the name of the matching environment, or null if no match is found
     */
    public static String findMatchingEnvironment(Path cwd, Map<String, List<String>> envPaths) {
        if (cwd == null || envPaths == null || envPaths.isEmpty()) {
            return null;
        }

        Path normalizedCwd = normalizePath(cwd);
        if (normalizedCwd == null) {
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

    /**
     * Expands a path string by replacing ~ with the user's home directory and normalizes it.
     *
     * @param pathStr the path string to expand
     * @return the normalized path, or null if the path is invalid
     */
    public static Path expandAndNormalize(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return null;
        }

        try {
            String expanded = pathStr;
            String home = System.getProperty("user.home", "");

            if (pathStr.equals("~")) {
                expanded = home;
            } else if (pathStr.startsWith("~/") || pathStr.startsWith("~\\")) {
                if (!home.isEmpty()) {
                    String separator = pathStr.startsWith("~\\") ? "\\" : "/";
                    expanded = home + separator + pathStr.substring(2);
                }
            }

            return Paths.get(expanded).toAbsolutePath().normalize();
        } catch (Exception e) {
            Main.log().warn("Invalid path '{}' in path mappings: {}", pathStr, e.getMessage());
            return null;
        }
    }

    private static Path normalizePath(Path path) {
        try {
            return path.toAbsolutePath().normalize();
        } catch (Exception e) {
            Main.log().warn("Could not normalize path '{}': {}", path, e.getMessage());
            return null;
        }
    }
}