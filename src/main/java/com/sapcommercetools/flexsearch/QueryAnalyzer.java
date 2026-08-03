package com.sapcommercetools.flexsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stateless, string-level heuristic linter for FlexibleSearch statements.
 *
 * <p>It does not parse the query into an AST; instead it applies a handful of
 * cheap textual checks that catch the most common performance foot-guns seen in
 * SAP Commerce projects:
 *
 * <ul>
 *   <li><b>{@code SELECT *}</b> — projecting everything defeats column
 *       pruning and inflates the result set.</li>
 *   <li><b>missing {@code WHERE}</b> — an unbounded predicate means a full
 *       table scan.</li>
 *   <li><b>leading-wildcard {@code LIKE '%...'}</b> — a pattern that starts
 *       with {@code %} cannot use an index and forces a scan.</li>
 * </ul>
 *
 * <p>Each finding is returned as a short, human-readable warning. An empty list
 * means no heuristic fired.
 */
public final class QueryAnalyzer {

    /** {@code LIKE} followed by a quoted literal that starts with {@code %}. */
    private static final Pattern LEADING_WILDCARD_LIKE =
            Pattern.compile("LIKE\\s+'%", Pattern.CASE_INSENSITIVE);

    private QueryAnalyzer() {
    }

    /**
     * Analyzes a FlexibleSearch statement and returns any heuristic warnings.
     *
     * @param query the FlexibleSearch statement (may be null/blank)
     * @return an ordered, possibly empty list of warning messages
     */
    public static List<String> analyze(String query) {
        List<String> warnings = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return warnings;
        }

        String upper = query.toUpperCase();

        if (containsSelectStar(query)) {
            warnings.add("SELECT * projects all columns; list only the attributes you need.");
        }

        if (!upper.contains("WHERE")) {
            warnings.add("No WHERE clause; this query performs a full table scan.");
        }

        Matcher m = LEADING_WILDCARD_LIKE.matcher(query);
        if (m.find()) {
            warnings.add("Leading-wildcard LIKE ('%...') cannot use an index; consider a trailing wildcard or full-text search.");
        }

        return warnings;
    }

    /**
     * Detects a {@code SELECT *} projection, tolerating whitespace and an
     * optional brace/alias form such as {@code SELECT {*}} or {@code SELECT  *}.
     */
    private static boolean containsSelectStar(String query) {
        Pattern p = Pattern.compile("SELECT\\s+\\{?\\s*\\*", Pattern.CASE_INSENSITIVE);
        return p.matcher(query).find();
    }
}
