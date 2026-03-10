
package com.example.claudeshim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BinaryLocatorTest {

    @Test
    void testBinaryLocatorDoesNotCrash(){

        String result = BinaryLocator.findRealClaude();

        assertNotNull(result);
    }
}
