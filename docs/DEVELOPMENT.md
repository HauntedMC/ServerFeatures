# Development Notes

This page is for contributors who want a fast, reliable local workflow.

## Local Setup

```bash
mvn -q -DskipTests compile
```

Useful commands during development:

```bash
mvn -q test
mvn -B verify
mvn -B -DskipTests checkstyle:check
mvn -B package
```

## Recommended Workflow

1. Create a branch for one focused change.
2. Implement behavior and tests in the same pass.
3. Run local validation (`test` at minimum).
4. Update docs when behavior or operator workflow changes.
5. Open a PR with context, impact, and migration notes when relevant.

## Engineering Guidelines

- Keep feature boundaries clean; avoid unnecessary cross-feature coupling.
- Prefer typed config access (`ConfigView` / `ConfigNode`) over raw casts.
- Make external calls fail-safe and time-bounded.
- Ensure disable/reload paths release resources cleanly.
- Keep logic testable; avoid burying behavior in hard-to-reach static paths.

## Feature Authoring Checklist

When adding a new feature module:

1. Implement metadata in `features/<feature>/meta/Meta`.
2. Extend `BukkitBaseFeature` and define `getDefaultConfig()` / `getDefaultMessages()`.
3. Register listeners/tasks/commands through lifecycle managers.
4. Add feature tests under the mirrored `src/test/java/...` package path.
5. Validate enable/disable/reload behavior with no leaked resources.

## Before You Open a PR

- Build succeeds locally.
- Relevant tests pass.
- New behavior is covered by tests.
- Operationally important failures are logged clearly.
