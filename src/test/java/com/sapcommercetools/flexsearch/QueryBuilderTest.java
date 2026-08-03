package com.sapcommercetools.flexsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class QueryBuilderTest {

    /** A small type model reused across the tests. */
    private TypeModel model() {
        return TypeModel.builder()
                .type("Product", "code", "name", "price")
                .type("Order", "code", "total")
                .build();
    }

    @Test
    void builds_exact_query_string_with_select_where_and_order() {
        String query = QueryBuilder.from(model(), "Product", "p")
                .select("code", "name")
                .where("code", "=", "code")
                .orderBy("name", true)
                .build();

        assertEquals(
                "SELECT {p:code}, {p:name} FROM {Product AS p} WHERE {p:code} = ?code ORDER BY {p:name} ASC",
                query);
    }

    @Test
    void builds_descending_order() {
        String query = QueryBuilder.from(model(), "Product", "p")
                .select("code")
                .orderBy("price", false)
                .build();

        assertEquals("SELECT {p:code} FROM {Product AS p} ORDER BY {p:price} DESC", query);
    }

    @Test
    void unknown_type_throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> QueryBuilder.from(model(), "Voucher", "v"));
        assertTrue(ex.getMessage().contains("unknown type 'Voucher'"));
    }

    @Test
    void unknown_attribute_in_select_throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> QueryBuilder.from(model(), "Product", "p").select("bogus"));
        assertEquals("unknown attribute 'bogus' on Product", ex.getMessage());
    }

    @Test
    void unknown_attribute_in_where_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QueryBuilder.from(model(), "Product", "p")
                        .select("code")
                        .where("bogus", "=", "x"));
    }

    @Test
    void unknown_attribute_in_orderBy_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QueryBuilder.from(model(), "Product", "p")
                        .select("code")
                        .orderBy("bogus", true));
    }

    @Test
    void unsupported_operator_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QueryBuilder.from(model(), "Product", "p")
                        .select("code")
                        .where("code", "==", "x"));
    }

    @Test
    void missing_select_throws_illegal_state() {
        assertThrows(
                IllegalStateException.class,
                () -> QueryBuilder.from(model(), "Product", "p").build());
    }

    @Test
    void empty_select_call_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QueryBuilder.from(model(), "Product", "p").select());
    }

    @Test
    void multiple_wheres_are_anded() {
        String query = QueryBuilder.from(model(), "Product", "p")
                .select("code")
                .where("code", "=", "code")
                .where("price", ">=", "min")
                .build();

        assertEquals(
                "SELECT {p:code} FROM {Product AS p} WHERE {p:code} = ?code AND {p:price} >= ?min",
                query);
    }

    @Test
    void analyzer_flags_missing_where_full_scan() {
        List<String> warnings = QueryAnalyzer.analyze(
                "SELECT {p:code} FROM {Product AS p}");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("full table scan")));
    }

    @Test
    void analyzer_flags_select_star() {
        List<String> warnings = QueryAnalyzer.analyze("SELECT * FROM {Product AS p}");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("SELECT *")));
    }

    @Test
    void analyzer_flags_leading_wildcard_like() {
        List<String> warnings = QueryAnalyzer.analyze(
                "SELECT {p:code} FROM {Product AS p} WHERE {p:name} LIKE '%phone'");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Leading-wildcard LIKE")));
    }

    @Test
    void analyzer_clean_query_has_no_warnings() {
        List<String> warnings = QueryAnalyzer.analyze(
                "SELECT {p:code} FROM {Product AS p} WHERE {p:code} = ?code");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void analyzer_null_query_is_empty() {
        assertTrue(QueryAnalyzer.analyze(null).isEmpty());
    }

    @Test
    void type_model_reports_known_and_unknown() {
        TypeModel m = model();
        assertTrue(m.hasType("Product"));
        assertFalse(m.hasType("Voucher"));
        assertTrue(m.hasAttribute("Product", "code"));
        assertFalse(m.hasAttribute("Product", "bogus"));
    }
}
