package com.proaut.claudeshim;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class BinaryLocatorTest {

    @Test
    void testBinaryLocatorDoesNotCrash() {
        BinaryLocator.findRealClaude();
    }

    @Test
    void skipsCurrentShimPathAndReturnsNextClaude() throws Exception {
        Path tempDir = Files.createTempDirectory("binary-locator-test");
        Path selfDir = Files.createDirectory(tempDir.resolve("self"));
        Path realDir = Files.createDirectory(tempDir.resolve("real"));

        Path selfClaude = Files.createFile(selfDir.resolve("claude"));
        Path realClaude = Files.createFile(realDir.resolve("claude"));
        selfClaude.toFile().setExecutable(true, false);
        realClaude.toFile().setExecutable(true, false);

        String path = selfDir + File.pathSeparator + realDir;

        String result = BinaryLocator.findRealClaude(path, selfClaude);

        assertEquals(realClaude.toRealPath().toString(), Path.of(result).toRealPath().toString());
    }

    @Test
    void resolvesWindowsExecutableNamesAndSkipsSelf() throws Exception {
        Path tempDir = Files.createTempDirectory("binary-locator-win-test");
        Path selfDir = Files.createDirectory(tempDir.resolve("self"));
        Path realDir = Files.createDirectory(tempDir.resolve("real"));

        Path selfClaude = Files.createFile(selfDir.resolve("claude.exe"));
        Path realClaude = Files.createFile(realDir.resolve("claude.cmd"));

        String path = selfDir + File.pathSeparator + realDir;

        String result = BinaryLocator.findRealClaude(path, selfClaude, "Windows 11");

        assertNotNull(result);
        assertEquals(realClaude.toAbsolutePath().normalize(), Path.of(result).toAbsolutePath().normalize());
    }

    @Test
    void returnsNullWhenPathIsNull() {
        assertNull(BinaryLocator.findRealClaude(null, null));
    }
}
