package com.sapcommercetools.flexsearch;

/**
 * Builds a validated FlexibleSearch statement from type-safe metamodel references, rejecting unknown types/attributes at construction time.
 *
 * <p>This is the core abstraction of <b>flexsearch-dx</b>. The starter implementation
 * below is intentionally minimal — a foundation that documents the intended
 * contract and gives tests something real to exercise.
 */
public final class QueryBuilder {

    /**
     * Returns a human-readable description of what this component does.
     * Replace with the real behaviour as the project grows.
     */
    public String describe() {
        return "flexsearch-dx: A type-safe FlexibleSearch DSL and performance analyzer for SAP Commerce — queries that fail at compile time, not in production.";
    }

    /**
     * Placeholder for the primary operation. Kept trivial and total so the
     * scaffold builds and tests pass on a clean checkout.
     *
     * @param input a caller-supplied token
     * @return {@code true} when the input is non-blank
     */
    public boolean accepts(String input) {
        return input != null && !input.isBlank();
    }
}
