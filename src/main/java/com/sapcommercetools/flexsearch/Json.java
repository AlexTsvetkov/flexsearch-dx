package com.sapcommercetools.flexsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser adequate for the small, well-formed
 * responses returned by the SAP Commerce HAC FlexibleSearch console.
 *
 * <p>This is intentionally <em>not</em> a general-purpose library: it is a
 * hand-written recursive-descent parser that understands the JSON grammar
 * (objects, arrays, strings with the standard escape sequences, numbers,
 * {@code true}/{@code false}/{@code null}) well enough to decode the
 * {@code {query, executionTime, resultCount, exception, resultList, headers}}
 * shape without pulling in Jackson or any other dependency.
 *
 * <p>Parsed values are mapped to plain JDK types:
 * <ul>
 *   <li>object → {@link java.util.Map}&lt;String, Object&gt; (insertion ordered)</li>
 *   <li>array → {@link java.util.List}&lt;Object&gt;</li>
 *   <li>string → {@link String}</li>
 *   <li>number → {@link Long} when integral, otherwise {@link Double}</li>
 *   <li>{@code true}/{@code false} → {@link Boolean}</li>
 *   <li>{@code null} → {@code null}</li>
 * </ul>
 */
final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    /**
     * Parses a JSON document into plain JDK objects.
     *
     * @param text the JSON text (non-null)
     * @return the parsed value (Map, List, String, Long, Double, Boolean or null)
     * @throws IllegalArgumentException if the text is not well-formed JSON
     */
    static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("json text must not be null");
        }
        Json p = new Json(text);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("trailing content at index " + p.pos);
        }
        return v;
    }

    private Object readValue() {
        if (pos >= src.length()) {
            throw err("unexpected end of input");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            expect(':');
            skipWs();
            map.put(key, readValue());
            skipWs();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw err("expected ',' or '}' in object");
            }
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw err("expected ',' or ']' in array");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw err("unterminated string");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw err("unterminated escape");
                }
                char e = src.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > src.length()) {
                            throw err("truncated unicode escape");
                        }
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw err("invalid escape '\\" + e + "'");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        boolean fractional = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                fractional = fractional || c == '.' || c == 'e' || c == 'E';
                pos++;
            } else {
                break;
            }
        }
        String num = src.substring(start, pos);
        if (num.isEmpty() || "-".equals(num)) {
            throw err("invalid number");
        }
        if (fractional) {
            return Double.parseDouble(num);
        }
        return Long.parseLong(num);
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw err("invalid literal");
    }

    private Object readNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw err("invalid literal");
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw err("unexpected end of input");
        }
        return src.charAt(pos);
    }

    private char next() {
        if (pos >= src.length()) {
            throw err("unexpected end of input");
        }
        return src.charAt(pos++);
    }

    private void expect(char c) {
        char actual = next();
        if (actual != c) {
            throw err("expected '" + c + "' but found '" + actual + "'");
        }
    }

    private IllegalArgumentException err(String message) {
        return new IllegalArgumentException(message + " (at index " + pos + ")");
    }
}
