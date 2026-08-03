package com.sapcommercetools.flexsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A type-safe, fluent builder that produces a valid SAP Commerce
 * <em>FlexibleSearch</em> statement while validating every type code and
 * attribute qualifier against a {@link TypeModel}.
 *
 * <p>The generated statement has the classic FlexibleSearch shape:
 *
 * <pre>{@code
 * SELECT {p:code}, {p:name} FROM {Product AS p} WHERE {p:code} = ?code ORDER BY {p:name} ASC
 * }</pre>
 *
 * <p>Validation happens as the query is assembled: an unknown root type is
 * rejected by {@link #from(TypeModel, String, String)}, and every attribute
 * passed to {@link #select}, {@link #where} or {@link #orderBy} must belong to
 * the root type's allowed set or an {@link IllegalArgumentException} is thrown.
 * The goal is that a query which compiles is structurally valid, so failures
 * surface at build time rather than as a runtime SQL error in production.
 *
 * <p>This builder is single-use and not thread-safe.
 */
public final class QueryBuilder {

    /** Comparison operators permitted in a {@code WHERE} predicate. */
    private static final Set<String> ALLOWED_OPS =
            Set.of("=", "<>", ">", "<", ">=", "<=", "LIKE");

    private final TypeModel model;
    private final String typeCode;
    private final String alias;

    private final List<String> selected = new ArrayList<>();
    private final List<Where> wheres = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();

    private QueryBuilder(TypeModel model, String typeCode, String alias) {
        this.model = model;
        this.typeCode = typeCode;
        this.alias = alias;
    }

    /**
     * Starts a query rooted at {@code typeCode} bound to the SELECT/WHERE
     * {@code alias}.
     *
     * @param model the type model to validate against (non-null)
     * @param typeCode the root type code; must exist in {@code model}
     * @param alias the alias used in {@code {alias:attr}} references (non-blank)
     * @return a fresh {@link QueryBuilder}
     * @throws IllegalArgumentException if the model/alias is invalid or the type
     *     is not present in the model
     */
    public static QueryBuilder from(TypeModel model, String typeCode, String alias) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
        if (!model.hasType(typeCode)) {
            throw new IllegalArgumentException("unknown type '" + typeCode + "'");
        }
        return new QueryBuilder(model, typeCode, alias);
    }

    /**
     * Adds one or more projection attributes to the {@code SELECT} list. May be
     * called more than once; duplicates are collapsed while preserving order.
     *
     * @param attrs attribute qualifiers, each legal on the root type
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if any attribute is unknown on the type
     */
    public QueryBuilder select(String... attrs) {
        if (attrs == null || attrs.length == 0) {
            throw new IllegalArgumentException("select requires at least one attribute");
        }
        for (String attr : attrs) {
            validateAttribute(attr);
            if (!selected.contains(attr)) {
                selected.add(attr);
            }
        }
        return this;
    }

    /**
     * Adds a {@code WHERE} predicate of the form {@code {alias:attr} op ?param}.
     * Multiple predicates are combined with {@code AND} in call order.
     *
     * @param attr attribute qualifier legal on the root type
     * @param op one of {@code = &lt;&gt; &gt; &lt; &gt;= &lt;= LIKE}
     * @param paramName the FlexibleSearch bind-parameter name (without {@code ?})
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if the attribute is unknown, the operator
     *     is not allowed, or the parameter name is blank
     */
    public QueryBuilder where(String attr, String op, String paramName) {
        validateAttribute(attr);
        if (op == null || !ALLOWED_OPS.contains(op)) {
            throw new IllegalArgumentException("unsupported operator '" + op + "'");
        }
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName must not be blank");
        }
        wheres.add(new Where(attr, op, paramName));
        return this;
    }

    /**
     * Adds an {@code ORDER BY} term.
     *
     * @param attr attribute qualifier legal on the root type
     * @param asc {@code true} for {@code ASC}, {@code false} for {@code DESC}
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if the attribute is unknown on the type
     */
    public QueryBuilder orderBy(String attr, boolean asc) {
        validateAttribute(attr);
        orders.add(new Order(attr, asc));
        return this;
    }

    /**
     * Renders the accumulated clauses into a FlexibleSearch statement using
     * deterministic spacing.
     *
     * @return the FlexibleSearch query string
     * @throws IllegalStateException if no projection was selected
     */
    public String build() {
        if (selected.isEmpty()) {
            throw new IllegalStateException("no attributes selected; call select(...) before build()");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ref(selected.get(i)));
        }

        sb.append(" FROM {").append(typeCode).append(" AS ").append(alias).append('}');

        if (!wheres.isEmpty()) {
            sb.append(" WHERE ");
            for (int i = 0; i < wheres.size(); i++) {
                if (i > 0) {
                    sb.append(" AND ");
                }
                Where w = wheres.get(i);
                sb.append(ref(w.attr)).append(' ').append(w.op).append(" ?").append(w.paramName);
            }
        }

        if (!orders.isEmpty()) {
            sb.append(" ORDER BY ");
            for (int i = 0; i < orders.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Order o = orders.get(i);
                sb.append(ref(o.attr)).append(' ').append(o.asc ? "ASC" : "DESC");
            }
        }

        return sb.toString();
    }

    /** Renders an {@code {alias:attr}} attribute reference. */
    private String ref(String attr) {
        return "{" + alias + ":" + attr + "}";
    }

    private void validateAttribute(String attr) {
        if (attr == null || attr.isBlank()) {
            throw new IllegalArgumentException("attribute must not be blank");
        }
        if (!model.hasAttribute(typeCode, attr)) {
            throw new IllegalArgumentException("unknown attribute '" + attr + "' on " + typeCode);
        }
    }

    private record Where(String attr, String op, String paramName) {
    }

    private record Order(String attr, boolean asc) {
    }
}
