# flexsearch-dx

**A type-safe FlexibleSearch DSL and performance analyzer for SAP Commerce — queries that fail at compile time, not in production.**

**🌐 Live site: https://alextsvetkov.github.io/flexsearch-dx/**

> ✅ **Status:** working core. A real, tested implementation of the core capability runs offline (no live SAP Commerce instance needed); unit tests pass in CI. Not yet a production product — see [Roadmap](#roadmap) for what would make it one.

**Stack:** Java 21 + Gradle.

---

## The problem

FlexibleSearch is stringly-typed: queries break silently when the model changes, and hide N+1 and missing-index performance cliffs. Generic SQL tools don't understand the type-system→table mapping or the region cache.

## The solution

(a) A **type-safe query builder** generated from the type model so queries fail at compile time; (b) a **query analyzer** that explains plans, flags N+1 and cache-hostile patterns, and suggests indexes — SAP-Commerce-aware.

See the [project site](https://alextsvetkov.github.io/flexsearch-dx/) for the full benefits narrative.

## Design principles

1. **Compile-time safety** — Queries are built from generated, type-checked metamodel classes — a renamed attribute is a build error, not a 2am incident.
2. **Fluent & readable** — A jOOQ-style builder that reads like the query it produces.
3. **Performance-aware** — The analyzer knows the type→table mapping and flags N+1, full scans and cache-hostile access.
4. **Zero runtime surprises** — What compiles is what runs.

## Core abstraction

`QueryBuilder` — Builds a validated FlexibleSearch statement from type-safe metamodel references, rejecting unknown types/attributes at construction time.

## Features

| Capability | Description |
|------------|-------------|
| ``QueryBuilder`` | Fluent, type-safe construction of FlexibleSearch statements. |
| ``select().from().where()`` | Metamodel-driven column and type references. |
| ``QueryAnalyzer`` | Static + plan-based detection of N+1 and missing indexes. |
| `Index advisor` | Suggests indexes for hot query shapes. |

## Quick start

```bash
gradle build
gradle test
```

## Usage

Everything below is distilled from the runnable, heavily-commented tutorial at
`src/main/java/com/sapcommercetools/flexsearch/examples/Example.java`. The
`Output:` blocks are the **real stdout** captured from running it.

### 1. Build a typed query string

A `TypeModel` is the offline stand-in for `items.xml`: it maps each type code to
its legal attributes. `QueryBuilder` renders a valid FlexibleSearch string and
validates every attribute against that model as you go.

```java
TypeModel model = TypeModel.builder()
        .type("Product", "code", "name", "catalogVersion", "creationtime")
        .type("Currency", "isocode", "name", "active", "conversion")
        .build();

// Multiple AND-combined predicates + ORDER BY, every attribute type-checked.
String q = QueryBuilder.from(model, "Product", "p")
        .select("code", "name", "creationtime")
        .where("catalogVersion", "=", "cv")
        .where("name", "LIKE", "namePattern")
        .orderBy("creationtime", false) // false => DESC
        .orderBy("code", true)          // true  => ASC
        .build();
System.out.println(q);
```

```text
Output:
SELECT {p:code}, {p:name}, {p:creationtime} FROM {Product AS p} WHERE {p:catalogVersion} = ?cv AND {p:name} LIKE ?namePattern ORDER BY {p:creationtime} DESC, {p:code} ASC
```

Bind parameters (`?cv`, `?namePattern`) are the only thing emitted for values —
never a literal — so queries are injection-safe by construction.

### 2. Unknown attributes are rejected at build time (fail fast)

A typo like `nam` instead of `name`, or an unregistered type, throws
immediately — long before the query would hit the database as a runtime SQL
error.

```java
try {
    QueryBuilder.from(model, "Product", "p")
            .select("code", "nam") // typo: 'nam' is not a Product attribute
            .build();
} catch (IllegalArgumentException e) {
    System.out.println("Rejected as expected: " + e.getMessage());
}

try {
    QueryBuilder.from(model, "Widget", "w"); // 'Widget' was never registered
} catch (IllegalArgumentException e) {
    System.out.println("Rejected as expected: " + e.getMessage());
}
```

```text
Output:
Rejected as expected: unknown attribute 'nam' on Product
Rejected as expected: unknown type 'Widget'
```

### 3. Lint queries with QueryAnalyzer (performance heuristics)

`QueryAnalyzer.analyze(...)` is a cheap, string-level linter for the most common
performance foot-guns. A clean, bounded query returns an empty list.

```java
QueryAnalyzer.analyze("SELECT {p:code} FROM {Product AS p}");                              // full scan
QueryAnalyzer.analyze("SELECT * FROM {Product AS p} WHERE {p:code} = ?code");              // SELECT *
QueryAnalyzer.analyze("SELECT {p:code} FROM {Product AS p} WHERE {p:name} LIKE '%phone'"); // leading %
```

```text
Output:
- Missing WHERE (full scan):
    WARN:   No WHERE clause; this query performs a full table scan.
- SELECT * projection:
    WARN:   SELECT * projects all columns; list only the attributes you need.
- Leading-wildcard LIKE:
    WARN:   Leading-wildcard LIKE ('%...') cannot use an index; consider a trailing wildcard or full-text search.
```

### Running it against a live HAC instance

`LiveExample` logs in to a real HAC instance and executes
`SELECT {pk},{isocode} FROM {Currency}` via `HacFlexibleSearchClient`. With no
env vars set it just prints how to configure them, so it is always safe to run.

```java
// Reads COMMERCE_BASE_URL / COMMERCE_USER / COMMERCE_PASSWORD from the env.
HacConfig config = HacConfig.fromEnv();
HacFlexibleSearchClient client = new HacFlexibleSearchClient(config);
FlexResult result = client.execute("SELECT {pk},{isocode} FROM {Currency}");
result.rows().forEach(System.out::println);
```

Gradle is not required — compile and run everything above with the JDK (Java 21):

```bash
find src/main/java -name '*.java' | xargs javac -d /tmp/ex-flex

# Offline tutorial (produces the Output blocks above):
java -cp /tmp/ex-flex com.sapcommercetools.flexsearch.examples.Example

# Live run (guidance mode if COMMERCE_BASE_URL is unset):
java -cp /tmp/ex-flex com.sapcommercetools.flexsearch.examples.LiveExample

# Live run against a local sample instance:
COMMERCE_BASE_URL=https://localhost:9002 \
COMMERCE_USER=admin COMMERCE_PASSWORD=nimda COMMERCE_INSECURE_TLS=true \
java -cp /tmp/ex-flex com.sapcommercetools.flexsearch.examples.LiveExample
```

## Roadmap

- [x] Implement the core capability with real logic + unit tests.
- [ ] Broaden coverage (more rules/edge cases) beyond the first working version.
- [ ] Wire against a live SAP Commerce / BTP environment.
- [ ] Publish artifacts and usage docs.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Conventional commits; generated code stays out of version control.

## License

[MIT](./LICENSE) © 2026 Aliaksandr Tsviatkou

## Honest assessment

> From the v2 self-critical analysis. Scores use **Gap · Value · Moat · Time-to-revenue · Risk** (for Risk, **higher = safer**). Prior art is named deliberately — "no competitor" is almost never true.

**Scores:** Gap 3 · Value 3 · Moat 2 · TTR 3 · Risk 4

- **Prior art / competition.** jOOQ / QueryDSL patterns are well known; nothing SAP-Commerce-aware. Real but small gap.
- **True differentiator.** Codegen from the type model + a region-cache-aware analyzer.
- **Kill criterion.** If teams keep hand-writing query strings because the codegen step annoys them, adoption stalls.
- **Verdict.** **OSS module**, not a standalone company.

This assessment is part of a broader, self-critical analysis of the whole tool suite (problem landscape, go-to-market, and an IP / conflict-of-interest review) maintained privately by the author.

---

*Part of a backend tooling suite for SAP Commerce Cloud. See [`commerce-mcp`](https://github.com/AlexTsvetkov/commerce-mcp) for the AI-native flagship.*
