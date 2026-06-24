package com.proaut.claudeshim;

/**
 * Prints a prominently colored startup banner showing the active environment.
 * Falls back to plain text when not running in an interactive terminal.
 */
public final class Banner {

    private static final String RESET = "\033[0m";
    private static final String BOLD  = "\033[1m";

    private Banner() {}

    public static void print(String envName, String color, String version) {
        String label = "claude-shim"
                + (version != null ? " " + version : "")
                + "  ▶  "
                + envName.toUpperCase();

        int width = Math.max(label.length() + 6, 42);
        String bar = "━".repeat(width);
        String line = center(label, width);

        boolean tty = System.console() != null;
        if (tty) {
            String ansi = ansiCode(color);
            System.err.println(ansi + BOLD + bar + RESET);
            System.err.println(ansi + BOLD + line + RESET);
            System.err.println(ansi + BOLD + bar + RESET);
        } else {
            System.err.println(bar);
            System.err.println(line);
            System.err.println(bar);
        }
    }

    private static String center(String s, int width) {
        int padding = (width - s.length()) / 2;
        int right = width - s.length() - padding;
        return " ".repeat(Math.max(0, padding)) + s + " ".repeat(Math.max(0, right));
    }

    static String ansiCode(String color) {
        if (color == null) return "\033[36m";
        String c = color.trim();
        if (c.startsWith("#") && c.length() == 7) {
            try {
                int r = Integer.parseInt(c.substring(1, 3), 16);
                int g = Integer.parseInt(c.substring(3, 5), 16);
                int b = Integer.parseInt(c.substring(5, 7), 16);
                return "\033[38;2;" + r + ";" + g + ";" + b + "m";
            } catch (NumberFormatException ignored) {}
        }
        return switch (c.toLowerCase()) {
            case "red"            -> "\033[31m";
            case "green"          -> "\033[32m";
            case "yellow"         -> "\033[33m";
            case "blue"           -> "\033[34m";
            case "magenta"        -> "\033[35m";
            case "cyan"           -> "\033[36m";
            case "white"          -> "\033[37m";
            case "bright_red"     -> "\033[91m";
            case "bright_green"   -> "\033[92m";
            case "bright_yellow"  -> "\033[93m";
            case "bright_blue"    -> "\033[94m";
            case "bright_magenta" -> "\033[95m";
            case "bright_cyan"    -> "\033[96m";
            case "bright_white"   -> "\033[97m";
            default               -> "\033[36m";
        };
    }
}
