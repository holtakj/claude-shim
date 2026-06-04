package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArgParsingTest {

    @Test
    void parsesEnvNameWithValue() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"--env", "customer-a", "chat"});

        assertEquals("customer-a", p.envName);
        assertFalse(p.forceMenu);
        assertEquals(List.of("chat"), p.forwardArgs);
    }

    @Test
    void bareEnvFlagForcesMenu() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"--env"});

        assertNull(p.envName);
        assertTrue(p.forceMenu);
        assertTrue(p.forwardArgs.isEmpty());
    }

    @Test
    void bareEnvFlagFollowedByOtherFlagForcesMenu() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"--env", "--help"});

        assertNull(p.envName);
        assertTrue(p.forceMenu);
        assertEquals(List.of("--help"), p.forwardArgs);
    }

    @Test
    void emptyEnvValueForcesMenu() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"--env", ""});

        assertNull(p.envName);
        assertTrue(p.forceMenu);
        assertTrue(p.forwardArgs.isEmpty());
    }

    @Test
    void envEqualsSyntaxWithValue() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"--env=customer-b"});

        assertEquals("customer-b", p.envName);
        assertFalse(p.forceMenu);
        assertTrue(p.forwardArgs.isEmpty());
    }

    @Test
    void envEqualsSyntaxEmptyForcesMenu() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"--env="});

        assertNull(p.envName);
        assertTrue(p.forceMenu);
        assertTrue(p.forwardArgs.isEmpty());
    }

    @Test
    void noEnvFlagLeavesEverythingForwarded() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"chat", "--verbose"});

        assertNull(p.envName);
        assertFalse(p.forceMenu);
        assertEquals(List.of("chat", "--verbose"), p.forwardArgs);
    }

    @Test
    void envFlagIsNotForwarded() {
        Main.ParsedArgs p = Main.parseArgs(new String[]{"--env", "prod", "--print"});

        assertEquals("prod", p.envName);
        assertEquals(List.of("--print"), p.forwardArgs);
    }
}