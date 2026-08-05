# Testing and Quality

Testing in this project is designed to catch regressions early while keeping contributor workflow practical.

## Test Structure

Tests live in each Maven module under `src/test/java` and mirror production package boundaries:

- API tests for public contracts and utility behavior
- framework tests for lifecycle, config, command, and loader logic
- feature tests for feature-specific logic and edge cases

Shared filesystem and proxy helpers live in `serverfeatures-testkit`; module-specific helpers remain next to their
tests.

## Local Commands

Run tests:

```bash
./mvnw -q test
```

Run the full quality gate:

```bash
./mvnw -B -ntp verify
```

The full gate treats actionable compiler warnings (including deprecations) as errors, rejects undeclared/unused
dependencies, and inspects the final shaded jar for required runtimes, relocation leaks, and accidentally bundled
platform plugins.

Run lint checks:

```bash
./mvnw -B -ntp -DskipTests checkstyle:check
```

## What to Test

When you change behavior, add or update tests near that behavior:

- feature changes: user-visible logic, edge cases, fallback behavior
- framework changes: lifecycle and dependency-resolution contracts
- API changes: conversion, fallback, error handling, and stability guarantees

Focus on regression-prone logic paths (branching rules, validation, parsing, state transitions).

## Test Quality Bar

Use these rules during authoring and review:

- prefer behavior assertions over "does not throw" smoke checks;
- avoid tests that only mirror declaration state (pure enum/constant checks);
- avoid pure getter/setter round-trip tests unless they protect a real invariant;
- assert observable outcomes for both happy and failure paths.

## Coverage Workflow

Use this when doing a full feature/class/method scan:

1. Run `./mvnw -B -ntp verify`.
2. Review each module's `target/site/jacoco/index.html` and sort by missed lines/branches.
3. Use module-level `jacoco.csv` files to find high-risk classes with high missed lines and branches.
4. Add tests for behavior-heavy methods first.

Prioritize methods with both high line miss and high branch count.

Coverage gates are enforced per module so API regressions cannot hide inside the much larger Paper bundle. The
current floors are explicit Maven properties and should only move upward as coverage improves.

## CI

CI runs:

- Checkstyle (`ci-lint.yml`)
- Tests and coverage (`ci-tests-and-coverage.yml`)
- Bundled Paper acceptance with DataProvider, DataRegistry, MySQL, and local-PDC AutoPickup startup

Tag pushes (`v*`) trigger release packaging and publication (`release-package.yml`).
