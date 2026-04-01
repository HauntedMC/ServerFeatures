# Testing and Quality

Testing in this project is designed to catch regressions early while keeping contributor workflow practical.

## Test Structure

Tests are organized under `src/test/java` and mirror production package boundaries:

- API tests for public contracts and utility behavior
- framework tests for lifecycle, config, command, and loader logic
- feature tests for feature-specific logic and edge cases

Shared helpers live under `src/test/java/nl/hauntedmc/serverfeatures/util`.

## Local Commands

Run tests:

```bash
mvn -q test
```

Run the full quality gate:

```bash
mvn -B verify
```

Run lint checks:

```bash
mvn -B -DskipTests checkstyle:check
```

Generate a local coverage report:

```bash
mvn -q test jacoco:report
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

1. Run `mvn -q test jacoco:report`.
2. Review `target/site/jacoco/index.html` and sort by missed lines/branches.
3. Use `target/site/jacoco/jacoco.csv` to find high-risk classes with high missed lines and branches.
4. Add tests for behavior-heavy methods first.

Prioritize methods with both high line miss and high branch count.

## CI

CI runs:

- Checkstyle (`ci-lint.yml`)
- Tests and coverage (`ci-tests-and-coverage.yml`)

Tag pushes (`v*`) trigger release packaging and publication (`release-package.yml`).
