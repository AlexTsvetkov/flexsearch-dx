package com.sapcommercetools.flexsearch;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QueryBuilderTest {

    private final QueryBuilder subject = new QueryBuilder();

    @Test
    void describes_itself() {
        assertTrue(subject.describe().startsWith("flexsearch-dx"));
    }

    @Test
    void accepts_non_blank_input() {
        assertTrue(subject.accepts("cart-123"));
        assertFalse(subject.accepts(" "));
        assertFalse(subject.accepts(null));
    }
}
