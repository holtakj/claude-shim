package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusLineTest {

    private static final String SCRIPT = "/home/user/.claude/claudeshim-statusline.sh";

    @Test
    void updatesExistingStatusLineObject() {
        String json = """
                {
                  "theme": "dark",
                  "statusLine": {"type": "command", "command": "old.sh"},
                  "something": true
                }
                """;
        String result = StatusLine.buildStatusLineJson(json, SCRIPT);
        assertTrue(result.contains("\"statusLine\": {\"type\": \"command\", \"command\": \""));
        assertTrue(result.contains(SCRIPT));
        assertTrue(result.contains("\"theme\": \"dark\""));
    }

    @Test
    void updatesExistingStatusLineMultiline() {
        String json = """
                {
                  "theme": "dark",
                  "statusLine": {
                    "type": "command",
                    "command": "old.sh"
                  },
                  "something": true
                }
                """;
        String result = StatusLine.buildStatusLineJson(json, SCRIPT);
        assertTrue(result.contains(SCRIPT));
        assertTrue(result.contains("\"theme\": \"dark\""));
    }

    @Test
    void appendsStatusLineWhenNotPresent() {
        String json = """
                {
                  "theme": "dark"
                }
                """;
        String result = StatusLine.buildStatusLineJson(json, SCRIPT);
        assertTrue(result.contains("\"statusLine\": {\"type\": \"command\", \"command\": \""));
        assertTrue(result.contains(SCRIPT));
        assertTrue(result.contains("\"theme\": \"dark\""));
    }

    @Test
    void appendsToMinimalObject() {
        String json = "{}";
        String result = StatusLine.buildStatusLineJson(json, SCRIPT);
        assertTrue(result.contains("\"statusLine\": {\"type\": \"command\", \"command\": \""));
        assertTrue(result.contains(SCRIPT));
    }

    @Test
    void appendsToEmptyObject() {
        String json = "{\n}";
        String result = StatusLine.buildStatusLineJson(json, SCRIPT);
        assertTrue(result.contains("\"statusLine\": {\"type\": \"command\", \"command\": \""));
        assertTrue(result.contains(SCRIPT));
    }

    @Test
    void removesStatusLineWithTrailingComma() {
        String json = """
                {
                  "theme": "dark",
                  "statusLine": {"type": "command", "command": "old.sh"},
                  "something": true
                }
                """;
        String result = StatusLine.stripStatusLine(json);
        assertFalse(result.contains("statusLine"));
        assertTrue(result.contains("\"theme\": \"dark\""));
        assertTrue(result.contains("\"something\": true"));
    }

    @Test
    void removesStatusLineAtEndWithoutComma() {
        String json = """
                {
                  "theme": "dark",
                  "statusLine": {"type": "command", "command": "old.sh"}
                }
                """;
        String result = StatusLine.stripStatusLine(json);
        assertFalse(result.contains("statusLine"));
        assertTrue(result.contains("\"theme\": \"dark\""));
    }

    @Test
    void removesOnlyStatusLine() {
        String json = """
                {
                  "statusLine": {"type": "command", "command": "old.sh"}
                }
                """;
        String result = StatusLine.stripStatusLine(json);
        assertFalse(result.contains("statusLine"));
        assertEquals("{\n}\n", result);
    }

    @Test
    void removesStatusLineFromMinimalObject() {
        String json = "{\n  \"statusLine\": {\"type\": \"command\", \"command\": \"old.sh\"}\n}";
        String result = StatusLine.stripStatusLine(json);
        assertFalse(result.contains("statusLine"));
        assertEquals("{\n}", result);
    }

    @Test
    void removeNoopWhenAbsent() {
        String json = """
                {
                  "theme": "dark"
                }
                """;
        String result = StatusLine.stripStatusLine(json);
        assertEquals(json, result);
    }

    @Test
    void envVarNameIsConstant() {
        assertEquals("CLAUDE_SHIM_ENV", StatusLine.getEnvVarName());
    }

    @Test
    void handlesMultilineStatusLineInStrip() {
        String json = """
                {
                  "theme": "dark",
                  "statusLine": {
                    "type": "command",
                    "command": "old.sh"
                  }
                }
                """;
        String result = StatusLine.stripStatusLine(json);
        assertFalse(result.contains("statusLine"));
        assertTrue(result.contains("\"theme\": \"dark\""));
    }

    @Test
    void buildStatusLineJsonHandlesEmptyString() {
        String result = StatusLine.buildStatusLineJson("", SCRIPT);
        assertTrue(result.contains(SCRIPT));
    }
}
