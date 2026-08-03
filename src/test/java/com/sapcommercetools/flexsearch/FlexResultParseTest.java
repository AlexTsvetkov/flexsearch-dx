package com.sapcommercetools.flexsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FlexResult#fromJson(String)} that feed canned JSON
 * matching the real HAC {@code /console/flexsearch/execute} response shape (see
 * {@code _gen/LIVE_CONTRACT.md}). These run in CI with no live instance and
 * prove the hand-written {@link Json} parser and field mapping are correct.
 */
class FlexResultParseTest {

    /** The exact shape returned by HAC for {@code SELECT {pk},{isocode} FROM {Currency}}. */
    private static final String SUCCESS_JSON = """
            {"query":"SELECT item_t0.PK, item_t0.p_isocode FROM currencies item_t0",\
            "executionTime":22,"resultCount":5,"exception":null,\
            "resultList":[["8796093087777","EUR"],["8796093120545","JPY"],\
            ["8796093153313","GBP"],["8796093186081","USD"],["8796093218849","CAD"]],\
            "headers":["PK","p_isocode"],"rawExecution":false,"dataSourceId":"master"}""";

    private static final String ERROR_JSON = """
            {"query":null,"executionTime":3,"resultCount":0,\
            "exception":"cannot search unknown type 'Bogus'",\
            "resultList":[],"headers":[],"rawExecution":false,"dataSourceId":"master"}""";

    @Test
    void parses_headers_rows_and_count() {
        FlexResult r = FlexResult.fromJson(SUCCESS_JSON);

        assertEquals(List.of("PK", "p_isocode"), r.headers());
        assertEquals(5, r.resultCount());
        assertEquals(22L, r.executionTimeMs());
        assertEquals(5, r.rows().size());
        assertEquals(List.of("8796093087777", "EUR"), r.rows().get(0));
        assertEquals(List.of("8796093218849", "CAD"), r.rows().get(4));
        assertTrue(r.headers().contains("PK"));
        assertNull(r.exception());
        assertFalse(r.hasError());
    }

    @Test
    void translated_sql_is_exposed() {
        FlexResult r = FlexResult.fromJson(SUCCESS_JSON);
        assertTrue(r.translatedSql().contains("currencies"));
    }

    @Test
    void error_response_exposes_exception_and_empty_rows() {
        FlexResult r = FlexResult.fromJson(ERROR_JSON);

        assertTrue(r.hasError());
        assertEquals("cannot search unknown type 'Bogus'", r.exception());
        assertTrue(r.rows().isEmpty());
        assertTrue(r.headers().isEmpty());
        assertEquals(0, r.resultCount());
    }

    @Test
    void parser_handles_escaped_strings() {
        String json = """
                {"query":"a \\"quoted\\" value \\\\ backslash","executionTime":0,\
                "resultCount":1,"exception":null,"resultList":[["x\\ty"]],"headers":["h"]}""";
        FlexResult r = FlexResult.fromJson(json);
        assertEquals("a \"quoted\" value \\ backslash", r.translatedSql());
        assertEquals(List.of("x\ty"), r.rows().get(0));
    }
}
