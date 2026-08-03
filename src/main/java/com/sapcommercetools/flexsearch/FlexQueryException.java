package com.sapcommercetools.flexsearch;

/**
 * Thrown by {@link HacFlexibleSearchClient#execute(String, int)} when the SAP
 * Commerce HAC console reports a server-side exception for a FlexibleSearch
 * statement (for example a syntax error or an unknown type/attribute).
 *
 * <p>Callers that prefer to inspect the failure without exception-handling can
 * use {@link HacFlexibleSearchClient#tryExecute(String, int)}, which returns the
 * raw {@link FlexResult} with its {@link FlexResult#exception()} populated.
 */
public class FlexQueryException extends RuntimeException {

    private final transient FlexResult result;

    /**
     * @param result the failed result carrying the HAC exception message
     */
    public FlexQueryException(FlexResult result) {
        super(result == null ? "FlexibleSearch failed" : result.exception());
        this.result = result;
    }

    /**
     * @return the raw {@link FlexResult} that carried the error (may be null)
     */
    public FlexResult result() {
        return result;
    }
}
