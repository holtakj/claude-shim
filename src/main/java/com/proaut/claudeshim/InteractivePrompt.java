package com.proaut.claudeshim;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.Function;

final class InteractivePrompt {

    private static final int KEY_TIMEOUT_MS = 100;

    private InteractivePrompt() {}

    static <T> T select(String title, List<T> items, Function<T, String> labelFn) throws Exception {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            String type = terminal.getType();
            if (type == null || Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type)) {
                throw new IllegalStateException("dumb terminal");
            }

            Terminal.SignalHandler prevInt = terminal.handle(Terminal.Signal.INT, sig -> {});
            Attributes prevAttrs = terminal.enterRawMode();
            PrintWriter writer = terminal.writer();
            NonBlockingReader reader = terminal.reader();

            writer.print("\033[?25l");
            writer.print(title);
            writer.print("\r\n");
            renderMenu(writer, items, labelFn, 0);
            writer.flush();

            int selected = 0;
            try {
                while (true) {
                    int c = reader.read();
                    if (c == -1) {
                        throw new IllegalStateException("eof");
                    }
                    if (c == 27) {
                        int n1 = reader.read(KEY_TIMEOUT_MS);
                        if (n1 == '[' || n1 == 'O') {
                            int n2 = reader.read(KEY_TIMEOUT_MS);
                            if (n2 == 'A') selected = (selected - 1 + items.size()) % items.size();
                            else if (n2 == 'B') selected = (selected + 1) % items.size();
                        } else {
                            clearMenu(writer, items.size());
                            throw new RuntimeException("aborted");
                        }
                    } else if (c == '\r' || c == '\n') {
                        clearMenu(writer, items.size());
                        return items.get(selected);
                    } else if (c == 'k') {
                        selected = (selected - 1 + items.size()) % items.size();
                    } else if (c == 'j') {
                        selected = (selected + 1) % items.size();
                    } else if (c == 3 || c == 4 || c == 'q') {
                        clearMenu(writer, items.size());
                        throw new RuntimeException("aborted");
                    } else if (c >= '1' && c <= '9') {
                        int idx = c - '1';
                        if (idx < items.size()) {
                            selected = idx;
                            clearMenu(writer, items.size());
                            return items.get(selected);
                        }
                    }
                    redrawMenu(writer, items, labelFn, selected);
                }
            } finally {
                writer.print("\033[?25h");
                writer.flush();
                terminal.setAttributes(prevAttrs);
                terminal.handle(Terminal.Signal.INT, prevInt);
            }
        }
    }

    private static <T> void renderMenu(PrintWriter w, List<T> items, Function<T, String> labelFn, int selected) {
        for (int i = 0; i < items.size(); i++) {
            String label = labelFn.apply(items.get(i));
            if (i == selected) {
                w.printf("\033[36m> %s\033[0m\r\n", label);
            } else {
                w.printf("  %s\r\n", label);
            }
        }
    }

    private static <T> void redrawMenu(PrintWriter w, List<T> items, Function<T, String> labelFn, int selected) {
        w.printf("\033[%dA", items.size());
        for (int i = 0; i < items.size(); i++) {
            String label = labelFn.apply(items.get(i));
            w.print("\033[2K");
            if (i == selected) {
                w.printf("\033[36m> %s\033[0m\r\n", label);
            } else {
                w.printf("  %s\r\n", label);
            }
        }
        w.flush();
    }

    private static void clearMenu(PrintWriter w, int menuLines) {
        w.printf("\033[%dA\r\033[J", menuLines + 1);
        w.flush();
    }
}
