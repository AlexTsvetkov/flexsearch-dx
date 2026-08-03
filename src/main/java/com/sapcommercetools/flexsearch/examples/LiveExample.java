package com.sapcommercetools.flexsearch.examples;

import com.sapcommercetools.flexsearch.FlexResult;
import com.sapcommercetools.flexsearch.HacConfig;
import com.sapcommercetools.flexsearch.HacFlexibleSearchClient;
import java.util.List;

/**
 * A runnable, LIVE mini-tutorial for <b>flexsearch-dx</b> that talks to a real
 * SAP Commerce HAC (hybris administration console) instance.
 *
 * <p>It is safe to run with no configuration: when {@code COMMERCE_BASE_URL} is
 * not set it simply prints how to set the environment variables and exits. When
 * the variable IS set it logs in and executes a trivial query
 * ({@code SELECT {pk},{isocode} FROM {Currency}}), then prints the returned
 * rows.
 *
 * <p>Environment variables (read via {@link HacConfig#fromEnv()}):
 * <ul>
 *   <li>{@code COMMERCE_BASE_URL} — e.g. {@code https://localhost:9002} (required)</li>
 *   <li>{@code COMMERCE_USER} — HAC user (default {@code admin})</li>
 *   <li>{@code COMMERCE_PASSWORD} — HAC password (default {@code nimda})</li>
 *   <li>{@code COMMERCE_INSECURE_TLS} — {@code true} to trust the local
 *       self-signed cert (needed for {@code https://localhost:9002})</li>
 * </ul>
 *
 * <p>Run it with:
 * <pre>{@code
 * find src/main/java -name '*.java' | xargs javac -d /tmp/ex-flex
 *
 * # Offline (prints guidance):
 * java -cp /tmp/ex-flex com.sapcommercetools.flexsearch.examples.LiveExample
 *
 * # Live against a local sample instance:
 * COMMERCE_BASE_URL=https://localhost:9002 \
 * COMMERCE_USER=admin COMMERCE_PASSWORD=nimda COMMERCE_INSECURE_TLS=true \
 * java -cp /tmp/ex-flex com.sapcommercetools.flexsearch.examples.LiveExample
 * }</pre>
 */
public final class LiveExample {

    /** A tiny, always-safe read-only query used for the demo. */
    private static final String QUERY = "SELECT {pk},{isocode} FROM {Currency}";

    private LiveExample() {
    }

    public static void main(String[] args) throws Exception {
        // fromEnv() throws IllegalStateException when COMMERCE_BASE_URL is unset,
        // so we check the variable first and print guidance rather than crash.
        String baseUrl = System.getenv(HacConfig.ENV_BASE_URL);
        if (baseUrl == null || baseUrl.isBlank()) {
            printGuidance();
            return;
        }

        HacConfig config = HacConfig.fromEnv();
        System.out.println("Connecting to HAC at " + config.baseUrl()
                + " as '" + config.user() + "' (insecureTls=" + config.insecureTls() + ")");

        HacFlexibleSearchClient client = new HacFlexibleSearchClient(config);

        // 1. Log in (Spring Security form login + AJAX CSRF token capture).
        client.login();
        System.out.println("Login succeeded.");

        // 2. Execute the query. execute(...) throws FlexQueryException on a
        // server-side error; use tryExecute(...) if you prefer to inspect
        // FlexResult.hasError() yourself.
        System.out.println("Executing: " + QUERY);
        FlexResult result = client.execute(QUERY, 100);

        // 3. Print the result set.
        System.out.println("Rows returned: " + result.resultCount()
                + " (server time " + result.executionTimeMs() + " ms)");
        System.out.println("Headers: " + result.headers());
        int i = 1;
        for (List<String> row : result.rows()) {
            System.out.println("  " + (i++) + ": " + row);
        }
        if (result.translatedSql() != null) {
            System.out.println("Translated SQL: " + result.translatedSql());
        }
    }

    private static void printGuidance() {
        System.out.println("COMMERCE_BASE_URL is not set — running in guidance mode (no network call).");
        System.out.println();
        System.out.println("To run live against a local SAP Commerce sample instance, set:");
        System.out.println("  export COMMERCE_BASE_URL=https://localhost:9002");
        System.out.println("  export COMMERCE_USER=admin           # optional, defaults to 'admin'");
        System.out.println("  export COMMERCE_PASSWORD=nimda        # optional, defaults to 'nimda'");
        System.out.println("  export COMMERCE_INSECURE_TLS=true     # trust the self-signed localhost cert");
        System.out.println();
        System.out.println("Then re-run:");
        System.out.println("  java -cp /tmp/ex-flex com.sapcommercetools.flexsearch.examples.LiveExample");
        System.out.println();
        System.out.println("It will log in and execute: " + QUERY);
    }
}
