# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository.

## What this project is

**flexsearch-dx** — A type-safe FlexibleSearch DSL and performance analyzer for SAP Commerce — queries that fail at compile time, not in production.

FlexibleSearch is stringly-typed: queries break silently when the model changes, and hide N+1 and missing-index performance cliffs. Generic SQL tools don't understand the type-system→table mapping or the region cache.

**Solution:** (a) A **type-safe query builder** generated from the type model so queries fail at compile time; (b) a **query analyzer** that explains plans, flags N+1 and cache-hostile patterns, and suggests indexes — SAP-Commerce-aware.

> Status: early scaffold. The core abstraction, a starter implementation and tests are real; most capabilities are documented intent, not yet built. Do not claim features exist that aren't in the code.

## Stack

Java 21 + Gradle (`java-library` plugin), JUnit 5.

## Project layout

- `src/main/java/**` — production code (core abstraction: `QueryBuilder`).
- `src/test/java/**` — JUnit 5 tests.
- `build.gradle`, `settings.gradle` — build config.
- `docs/` — GitHub Pages site (`index.html`, `.nojekyll`). Served at https://alextsvetkov.github.io/flexsearch-dx/.
- `.github/workflows/ci.yml` — CI (build + test on push/PR).

## Common commands

```bash
gradle build      # compile
gradle test       # run tests
```

## Conventions

- Prefer **constructor injection**; interface + `Default*` impl per service.
- No inline literals — use constants classes for log/config/exception strings.
- Keep the core abstraction (`QueryBuilder`) honest so implementations stay swappable.
- **Conventional commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`).
- Generated code (if any) stays out of version control.
- Keep `README.md`, `docs/index.html` and this file in sync when the scope changes.

## Working agreements for agents

- This is part of a **suite of SAP Commerce backend tools**; keep terminology consistent with the sibling repos (e.g. `commerce-mcp`, `flow-context`).
- When adding real behaviour, update the Roadmap in `README.md` and add tests in the same PR.
- Don't introduce a live-backend dependency into the default build — keep the scaffold green on a clean checkout.
- If you change the public contract, reflect it in the docs site and the README capability table.
