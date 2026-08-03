package com.sapcommercetools.flexsearch;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable, in-memory description of the SAP Commerce type system that a
 * {@link QueryBuilder} validates against.
 *
 * <p>Conceptually a {@code TypeModel} is a
 * {@code Map<String, Set<String>>} that maps each <em>type code</em> (for
 * example {@code "Product"}) to the set of <em>attribute qualifiers</em> that
 * are legal on that type (for example {@code "code"}, {@code "name"}). It plays
 * the role that {@code items.xml} / the generated model classes play at runtime
 * in a real Commerce project: it lets the DSL reject unknown types and
 * attributes at build time rather than failing in the database.
 *
 * <p>Instances are created through {@link #builder()} and are effectively
 * immutable: the internal maps and sets are defensively copied and the accessor
 * returns an unmodifiable view.
 */
public final class TypeModel {

    private final Map<String, Set<String>> types;

    private TypeModel(Map<String, Set<String>> types) {
        this.types = types;
    }

    /**
     * @return a new, empty {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param typeCode a Commerce type code, e.g. {@code "Product"}
     * @return {@code true} when the type is registered in this model
     */
    public boolean hasType(String typeCode) {
        return types.containsKey(typeCode);
    }

    /**
     * @param typeCode a registered type code
     * @param attribute an attribute qualifier
     * @return {@code true} when {@code attribute} is legal on {@code typeCode}
     */
    public boolean hasAttribute(String typeCode, String attribute) {
        Set<String> attrs = types.get(typeCode);
        return attrs != null && attrs.contains(attribute);
    }

    /**
     * @param typeCode a registered type code
     * @return an unmodifiable, insertion-ordered view of the attributes legal on
     *     the type, or an empty set when the type is unknown
     */
    public Set<String> attributesOf(String typeCode) {
        Set<String> attrs = types.get(typeCode);
        return attrs == null ? Collections.emptySet() : Collections.unmodifiableSet(attrs);
    }

    /**
     * Fluent builder for {@link TypeModel}. Not thread-safe; build once, then
     * share the immutable result.
     */
    public static final class Builder {

        private final Map<String, Set<String>> types = new HashMap<>();

        private Builder() {
        }

        /**
         * Registers a type together with its legal attribute qualifiers.
         * Calling this more than once for the same type merges the attributes.
         *
         * @param typeCode the Commerce type code (non-blank)
         * @param attributes the attribute qualifiers legal on the type
         * @return {@code this} for chaining
         * @throws IllegalArgumentException if {@code typeCode} is null/blank
         */
        public Builder type(String typeCode, String... attributes) {
            if (typeCode == null || typeCode.isBlank()) {
                throw new IllegalArgumentException("typeCode must not be blank");
            }
            Set<String> set = types.computeIfAbsent(typeCode, k -> new LinkedHashSet<>());
            if (attributes != null) {
                for (String a : attributes) {
                    if (a == null || a.isBlank()) {
                        throw new IllegalArgumentException(
                                "attribute qualifier must not be blank on " + typeCode);
                    }
                    set.add(a);
                }
            }
            return this;
        }

        /**
         * Builds an immutable {@link TypeModel} from the registered types.
         *
         * @return the model
         */
        public TypeModel build() {
            Map<String, Set<String>> copy = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : types.entrySet()) {
                copy.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            }
            return new TypeModel(copy);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypeModel other)) {
            return false;
        }
        return types.equals(other.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(types);
    }
}
