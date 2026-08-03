package com.sapcommercetools.flexsearch;

/**
 * Immutable connection configuration for {@link HacFlexibleSearchClient}.
 *
 * @param baseUrl the HAC base URL, e.g. {@code https://localhost:9002} (no trailing slash required)
 * @param user the HAC username, e.g. {@code admin}
 * @param password the HAC password
 * @param insecureTls when {@code true}, trust self-signed certificates and skip
 *     hostname verification — intended for local sample instances only
 */
public record HacConfig(String baseUrl, String user, String password, boolean insecureTls) {

    /** Environment variable holding the HAC base URL. */
    public static final String ENV_BASE_URL = "COMMERCE_BASE_URL";
    /** Environment variable holding the HAC username. */
    public static final String ENV_USER = "COMMERCE_USER";
    /** Environment variable holding the HAC password. */
    public static final String ENV_PASSWORD = "COMMERCE_PASSWORD";
    /** Environment variable toggling insecure (self-signed) TLS. */
    public static final String ENV_INSECURE_TLS = "COMMERCE_INSECURE_TLS";

    public HacConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        // Normalize away a trailing slash so path concatenation is predictable.
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    /**
     * Builds a config from the standard environment variables
     * ({@code COMMERCE_BASE_URL}, {@code COMMERCE_USER},
     * {@code COMMERCE_PASSWORD}, {@code COMMERCE_INSECURE_TLS}).
     *
     * @return a config populated from the environment
     * @throws IllegalStateException if {@code COMMERCE_BASE_URL} is unset/blank
     */
    public static HacConfig fromEnv() {
        String baseUrl = System.getenv(ENV_BASE_URL);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    ENV_BASE_URL + " is not set; cannot build a live HacConfig");
        }
        String user = orDefault(System.getenv(ENV_USER), "admin");
        String password = orDefault(System.getenv(ENV_PASSWORD), "nimda");
        boolean insecure = parseBool(System.getenv(ENV_INSECURE_TLS));
        return new HacConfig(baseUrl, user, password, insecure);
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static boolean parseBool(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes");
    }
}
