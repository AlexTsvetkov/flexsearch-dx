# flexsearch-dx

**A type-safe FlexibleSearch DSL and performance analyzer for SAP Commerce — queries that fail at compile time, not in production.**

> ⚠️ **Status:** early scaffold. The core abstraction, a starter implementation and tests are real; this is a foundation to build on, not a finished product. See [Roadmap](#roadmap).

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

## Roadmap

- [ ] Flesh out the core beyond the starter implementation.
- [ ] Wire against a live SAP Commerce / BTP environment.
- [ ] Publish artifacts and usage docs.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Conventional commits; generated code stays out of version control.

## License

[MIT](./LICENSE) © 2026 Aliaksandr Tsviatkou

---

*Part of a backend tooling suite for SAP Commerce Cloud. See [`commerce-mcp`](https://github.com/AlexTsvetkov/commerce-mcp) for the AI-native flagship.*
