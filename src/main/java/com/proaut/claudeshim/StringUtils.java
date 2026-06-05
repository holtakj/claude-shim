package com.proaut.claudeshim;

/**
 * Utility class for string operations.
 * <p>
 * Provides helper methods for common string operations that are not available in the standard Java library
 * or provide additional safety checks.
 */
final class StringUtils {

    private StringUtils() {}

    /**
     * Checks if a string is not null, not empty, and not blank.
     *
     * @param value the string to check
     * @return true if the string is not null, not empty, and not blank
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Trims and normalizes a string, returning null if the result is blank.
     *
     * @param value the string to normalize
     * @return the trimmed string, or null if blank
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}