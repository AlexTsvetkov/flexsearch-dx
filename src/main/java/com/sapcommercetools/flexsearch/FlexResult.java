package com.sapcommercetools.flexsearch;

import java.util.List;
import java.util.Map;

/**
 * The decoded result of a FlexibleSearch execution against the SAP Commerce HAC
 * console ({@code POST /console/flexsearch/execute}).
 *
 * <p>Field mapping from the raw HAC JSON:
 * <ul>
 *   <li>{@code headers} ← {@code headers} (column labels, e.g. {@code ["PK","p_isocode"]})</li>
 *   <li>{@code rows} ← {@code resultList} (each inner list is one row of string cells)</li>
 *   <li>{@code resultCount} ← {@code resultCount}</li>
 *   <li>{@code executionTimeMs} ← {@code executionTime}</li>
 *   <li>{@code translatedSql} ← {@code query} (the SQL HAC translated the query to)</li>
 *   <li>{@code exception} ← {@code exception} ({@code null} on success)</li>
 * </ul>
 *
 * @param headers the column headers; never null (empty on error)
 * @param rows the result rows, each a list of string cells; never null (empty on error)
 * @param resultCount the row count reported by HAC
 * @param executionTimeMs the server-side execution time in milliseconds
 * @param translatedSql the SQL the FlexibleSearch was translated to (may be null)
 * @param exception the server-side exception message, or {@code null} on success
 */
public record FlexResult(
        List<String> headers,
        List<List<String>> rows,
        int resultCount,
        long executionTimeMs,
        String translatedSql,
        String exception) {

    /**
     * @return {@code true} when HAC reported an exception for this query.
     */
    public boolean hasError() {
        return exception != null && !exception.isBlank();
    }

    /**
     * Decodes a HAC FlexibleSearch response body into a {@link FlexResult}.
     *
     * @param jsonBody the raw JSON response body (non-null)
     * @return the parsed result
     * @throws IllegalArgumentException if the body is not the expected shape
     */
    @SuppressWarnings("unchecked")
    public static FlexResult fromJson(String jsonBody) {
        Object parsed = Json.parse(jsonBody);
        if (!(parsed instanceof Map<?, ?> obj)) {
            throw new IllegalArgumentException("expected a JSON object at the top level");
        }
        Map<String, Object> map = (Map<String, Object>) obj;

        List<String> headers = toStringList(map.get("headers"));
        List<List<String>> rows = toRows(map.get("resultList"));
        int resultCount = toInt(map.get("resultCount"));
        long executionTime = toLong(map.get("executionTime"));
        String translatedSql = map.get("query") == null ? null : String.valueOf(map.get("query"));
        Object ex = map.get("exception");
        String exception = ex == null ? null : String.valueOf(ex);

        return new FlexResult(headers, rows, resultCount, executionTime, translatedSql, exception);
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>(list.size());
        for (Object o : list) {
            out.add(o == null ? null : String.valueOf(o));
        }
        return List.copyOf(out);
    }

    private static List<List<String>> toRows(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<List<String>> out = new java.util.ArrayList<>(list.size());
        for (Object rowObj : list) {
            out.add(toStringList(rowObj));
        }
        return List.copyOf(out);
    }

    private static int toInt(Object value) {
        return (int) toLong(value);
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
