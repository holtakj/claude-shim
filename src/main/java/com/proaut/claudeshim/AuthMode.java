package com.proaut.claudeshim;

import java.util.Locale;

/**
 * How an environment authenticates against Claude.
 */
public enum AuthMode {
    /** API key (or other credentials) supplied via {@code env.*} entries. Default. */
    API_KEY,
    /** Interactive {@code claude login} with a personal claude.ai account, isolated per environment. */
    LOGIN;

    /**
     * Parse the value of the {@code auth} property. Blank/null yields {@link #API_KEY}.
     *
     * @throws IllegalArgumentException for unknown values
     */
    static AuthMode parse(String value) {
        if (value == null || value.isBlank()) {
            return API_KEY;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "apikey", "api_key", "api-key" -> API_KEY;
            case "login" -> LOGIN;
            default -> throw new IllegalArgumentException(
                    "Unknown auth mode '" + value + "' (expected 'apikey' or 'login')");
        };
    }

    String label() {
        return this == LOGIN ? "login" : "api-key";
    }
}
