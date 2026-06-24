package com.proaut.claudeshim;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.Function;

/**
 * Interactive menu prompt using JLine raw-mode terminal.
 *
 * <p>Supports arrow keys (up/down), vim-style navigation (j/k),
 * numeric selection (1-9), Enter to confirm, and ESC/Ctrl+C/q to abort.</p>
 */
final class InteractivePrompt {

    private static final Logger log = LoggerFactory.getLogger(InteractivePrompt.class);

    // ---- ANSI escape sequences ----

    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";
    private static final String SELECTED_PREFIX_DEFAULT = "\033[36m> \033[0m";
    private static final String UNSELECTED_PREFIX = "  ";
    private static final String CLEAR_LINE = "\033[2K";
    private static final String CURSOR_UP = "\033[%dA";
    private static final String CLEAR_MENU = "\033[%dA\r\033[J";

    // ---- key codes ----

    private static final int KEY_ESC = 27;
    private static final int KEY_UP_ARROW = 'A';
    private static final int KEY_DOWN_ARROW = 'B';
    private static final int KEY_ENTER = '\r';
    private static final int KEY_NEWLINE = '\n';
    private static final int KEY_VIM_UP = 'k';
    private static final int KEY_VIM_DOWN = 'j';
    private static final int KEY_CTRL_C = 3;
    private static final int KEY_CTRL_D = 4;
    private static final int KEY_QUIT = 'q';

    private static final int ESCAPE_READ_TIMEOUT_MS = 100;

    private InteractivePrompt() {}

    /**
     * Display an interactive selection menu and return the chosen item.
     *
     * @param title   the menu title text
     * @param items   the list of selectable items
     * @param labelFn function to derive a display label from each item
     * @param <T>     the type of selectable item
     * @return the selected item
     * @throws IllegalStateException if the terminal is dumb or EOF is reached
     * @throws RuntimeException      if the user aborts (ESC, Ctrl+C, q)
     */
    static <T> T select(String title, List<T> items, Function<T, String> labelFn) throws Exception {
        return select(title, items, labelFn, item -> null);
    }

    static <T> T select(String title, List<T> items, Function<T, String> labelFn,
                        Function<T, String> colorFn) throws Exception {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            if (isDumbTerminal(terminal)) {
                throw new IllegalStateException("dumb terminal");
            }

            // Save original terminal state
            Terminal.SignalHandler prevInt = terminal.handle(Terminal.Signal.INT, sig -> {});
            Attributes prevAttrs = terminal.enterRawMode();
            PrintWriter writer = terminal.writer();
            NonBlockingReader reader = terminal.reader();

            // Hide cursor, render menu
            writer.print(HIDE_CURSOR);
            writer.print(title);
            writer.print("\r\n");
            renderMenu(writer, items, labelFn, colorFn, 0);
            writer.flush();

            int selected = 0;
            try {
                while (true) {
                    int c = reader.read();
                    if (c == KEY_ESC) {
                        int n1 = reader.read(ESCAPE_READ_TIMEOUT_MS);
                        if (n1 == '[' || n1 == 'O') {
                            int n2 = reader.read(ESCAPE_READ_TIMEOUT_MS);
                            if (n2 == KEY_UP_ARROW) selected = (selected - 1 + items.size()) % items.size();
                            else if (n2 == KEY_DOWN_ARROW) selected = (selected + 1) % items.size();
                            else { clearMenu(writer, items.size()); throw new RuntimeException("aborted"); }
                        } else {
                            clearMenu(writer, items.size());
                            throw new RuntimeException("aborted");
                        }
                    } else if (c == KEY_ENTER || c == KEY_NEWLINE) {
                        clearMenu(writer, items.size());
                        return items.get(selected);
                    } else if (c == KEY_VIM_UP) {
                        selected = (selected - 1 + items.size()) % items.size();
                    } else if (c == KEY_VIM_DOWN) {
                        selected = (selected + 1) % items.size();
                    } else if (c == KEY_CTRL_C || c == KEY_CTRL_D || c == KEY_QUIT) {
                        clearMenu(writer, items.size());
                        throw new RuntimeException("aborted");
                    } else if (c >= '1' && c <= '9') {
                        int idx = c - '1';
                        if (idx < items.size()) {
                            clearMenu(writer, items.size());
                            return items.get(idx);
                        }
                    } else if (c == -1) {
                        throw new IllegalStateException("eof");
                    }
                    // Otherwise: ignore unknown key
                    redrawMenu(writer, items, labelFn, colorFn, selected);
                }
            } finally {
                writer.print(SHOW_CURSOR);
                writer.flush();
                terminal.setAttributes(prevAttrs);
                terminal.handle(Terminal.Signal.INT, prevInt);
            }
        }
    }

    // ---- rendering ----

    private static <T> void renderMenu(PrintWriter w, List<T> items, Function<T, String> labelFn,
                                       Function<T, String> colorFn, int selected) {
        for (int i = 0; i < items.size(); i++) {
            String prefix = (i == selected) ? selectedPrefix(colorFn.apply(items.get(i))) : UNSELECTED_PREFIX;
            w.printf("%s%s\r\n", prefix, labelFn.apply(items.get(i)));
        }
    }

    private static <T> void redrawMenu(PrintWriter w, List<T> items, Function<T, String> labelFn,
                                       Function<T, String> colorFn, int selected) {
        w.printf(CURSOR_UP, items.size());
        for (int i = 0; i < items.size(); i++) {
            w.print(CLEAR_LINE);
            String prefix = (i == selected) ? selectedPrefix(colorFn.apply(items.get(i))) : UNSELECTED_PREFIX;
            w.printf("%s%s\r\n", prefix, labelFn.apply(items.get(i)));
        }
        w.flush();
    }

    private static String selectedPrefix(String hexColor) {
        String ansi = hexColor != null ? Banner.ansiCode(hexColor) : "\033[36m";
        return ansi + "> \033[0m";
    }

    private static void clearMenu(PrintWriter w, int menuLines) {
        // Move up past the menu, clear the current line, then clear everything below
        w.printf(CLEAR_MENU, menuLines + 1);
        w.flush();
    }

    // ---- helpers ----

    private static boolean isDumbTerminal(Terminal terminal) {
        String type = terminal.getType();
        return type == null
                || Terminal.TYPE_DUMB.equals(type)
                || Terminal.TYPE_DUMB_COLOR.equals(type);
    }
}
