package com.sapcommercetools.flexsearch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration test that talks to a live SAP Commerce HAC instance.
 *
 * <p><b>Gated:</b> the whole class is only enabled when {@code COMMERCE_BASE_URL}
 * is set, so it is SKIPPED in CI (which has no live instance) and runs only when
 * a developer points it at a reachable HAC via the standard environment
 * variables:
 *
 * <pre>{@code
 * COMMERCE_BASE_URL=https://localhost:9002 COMMERCE_USER=admin \
 *   COMMERCE_PASSWORD=nimda COMMERCE_INSECURE_TLS=true gradle test
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "COMMERCE_BASE_URL", matches = ".+")
class HacFlexibleSearchClientIT {

    private static HacFlexibleSearchClient client;

    @BeforeAll
    static void loginOnce() throws Exception {
        client = HacFlexibleSearchClient.fromEnv();
        client.login();
    }

    @Test
    void raw_query_returns_currency_rows() throws Exception {
        FlexResult result = client.execute("SELECT {pk},{isocode} FROM {Currency}", 20);

        assertFalse(result.hasError(), () -> "unexpected HAC error: " + result.exception());
        assertTrue(result.resultCount() > 0, "expected at least one currency row");
        assertTrue(result.headers().contains("PK"),
                () -> "headers should contain PK but were " + result.headers());
        assertFalse(result.rows().isEmpty(), "expected non-empty result rows");
    }

    @Test
    void query_built_via_query_builder_executes() throws Exception {
        TypeModel model = TypeModel.builder()
                .type("Currency", "pk", "isocode")
                .build();

        QueryBuilder qb = QueryBuilder.from(model, "Currency", "c")
                .select("isocode");

        FlexResult result = client.execute(qb, 20);

        assertFalse(result.hasError(), () -> "unexpected HAC error: " + result.exception());
        assertTrue(result.resultCount() > 0, "expected at least one row from the built query");
    }
}
