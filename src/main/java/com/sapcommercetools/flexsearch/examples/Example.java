package com.sapcommercetools.flexsearch.examples;

import com.sapcommercetools.flexsearch.QueryAnalyzer;
import com.sapcommercetools.flexsearch.QueryBuilder;
import com.sapcommercetools.flexsearch.TypeModel;
import java.util.List;

/**
 * A runnable, OFFLINE mini-tutorial for <b>flexsearch-dx</b>.
 *
 * <p>Nothing here touches the network — it exercises the three pure building
 * blocks of the library so a developer can learn the API purely by reading and
 * running this class:
 *
 * <ol>
 *   <li>{@link TypeModel} — an in-memory description of the Commerce type system
 *       (which attributes are legal on which type).</li>
 *   <li>{@link QueryBuilder} — a fluent, type-checked builder that renders a
 *       valid FlexibleSearch string and rejects unknown attributes at build
 *       time.</li>
 *   <li>{@link QueryAnalyzer} — a string-level linter that flags common
 *       performance foot-guns (full scans, {@code SELECT *}, leading-wildcard
 *       {@code LIKE}).</li>
 * </ol>
 *
 * <p>Run it with:
 * <pre>{@code
 * find src/main/java -name '*.java' | xargs javac -d /tmp/ex-flex
 * java -cp /tmp/ex-flex com.sapcommercetools.flexsearch.examples.Example
 * }</pre>
 */
public final class Example {

    private Example() {
    }

    public static void main(String[] args) {
        section("1. Build a TypeModel (the type system the builder validates against)");
        // A TypeModel maps each Commerce type code to the set of attributes that
        // are legal on it. This is the offline stand-in for items.xml. Here we
        // register Product and Currency with a handful of attributes each.
        TypeModel model = TypeModel.builder()
                .type("Product", "code", "name", "catalogVersion", "creationtime")
                .type("Currency", "isocode", "name", "active", "conversion")
                .build();
        System.out.println("Product known?  " + model.hasType("Product"));
        System.out.println("Product attrs:  " + model.attributesOf("Product"));
        System.out.println("Currency attrs: " + model.attributesOf("Currency"));

        section("2. Build valid FlexibleSearch strings with QueryBuilder");

        // 2a. A simple SELECT ... FROM with two projected columns.
        String q1 = QueryBuilder.from(model, "Product", "p")
                .select("code", "name")
                .build();
        System.out.println("SELECT only:\n  " + q1);

        // 2b. Add a WHERE predicate. The '?code' is a FlexibleSearch bind
        // parameter — the builder only emits the placeholder, never a literal,
        // which keeps queries injection-safe by construction.
        String q2 = QueryBuilder.from(model, "Product", "p")
                .select("code", "name")
                .where("code", "=", "code")
                .build();
        System.out.println("With WHERE:\n  " + q2);

        // 2c. Multiple WHERE predicates (AND-combined) plus an ORDER BY. Note
        // how every attribute is checked against the Product type as we go.
        String q3 = QueryBuilder.from(model, "Product", "p")
                .select("code", "name", "creationtime")
                .where("catalogVersion", "=", "cv")
                .where("name", "LIKE", "namePattern")
                .orderBy("creationtime", false) // false => DESC
                .orderBy("code", true)          // true  => ASC
                .build();
        System.out.println("WHERE + ORDER BY:\n  " + q3);

        // 2d. A Currency query — a different root type, different alias.
        String q4 = QueryBuilder.from(model, "Currency", "c")
                .select("isocode", "name", "active")
                .where("active", "=", "isActive")
                .orderBy("isocode", true)
                .build();
        System.out.println("Currency query:\n  " + q4);

        section("3. Unknown attributes are rejected at BUILD time (fail fast)");
        // The whole point of validating against a TypeModel: a typo like
        // 'nam' instead of 'name' throws immediately, long before the query
        // would ever hit the database as a runtime SQL error.
        try {
            QueryBuilder.from(model, "Product", "p")
                    .select("code", "nam") // <-- typo: 'nam' is not a Product attribute
                    .build();
            System.out.println("(unexpected: no exception thrown)");
        } catch (IllegalArgumentException expected) {
            System.out.println("Rejected as expected: " + expected.getMessage());
        }

        // An unknown ROOT TYPE is rejected just as eagerly by from(...).
        try {
            QueryBuilder.from(model, "Widget", "w"); // 'Widget' was never registered
            System.out.println("(unexpected: no exception thrown)");
        } catch (IllegalArgumentException expected) {
            System.out.println("Rejected as expected: " + expected.getMessage());
        }

        section("4. Lint queries with QueryAnalyzer (performance heuristics)");
        // The analyzer is a cheap, string-level linter. It does NOT parse SQL;
        // it just looks for the most common performance mistakes. A query that
        // trips no heuristic returns an empty list.

        // 4a. A well-formed, bounded query — expect zero warnings.
        printAnalysis("Clean, bounded query", q2);

        // 4b. No WHERE clause => full table scan.
        printAnalysis("Missing WHERE (full scan)",
                "SELECT {p:code} FROM {Product AS p}");

        // 4c. SELECT * defeats column pruning.
        printAnalysis("SELECT * projection",
                "SELECT * FROM {Product AS p} WHERE {p:code} = ?code");

        // 4d. A leading-wildcard LIKE ('%...') cannot use an index.
        printAnalysis("Leading-wildcard LIKE",
                "SELECT {p:code} FROM {Product AS p} WHERE {p:name} LIKE '%phone'");

        System.out.println();
        System.out.println("Done. For a LIVE run against a real HAC instance, see LiveExample.");
    }

    /** Runs the analyzer on one query and prints each warning (or "no warnings"). */
    private static void printAnalysis(String label, String query) {
        System.out.println("- " + label + ":");
        System.out.println("    query:  " + query);
        List<String> warnings = QueryAnalyzer.analyze(query);
        if (warnings.isEmpty()) {
            System.out.println("    result: no warnings");
        } else {
            for (String w : warnings) {
                System.out.println("    WARN:   " + w);
            }
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
