# Development Notes

This page is for contributors who want a fast, reliable local workflow.

## Local Setup

```bash
./mvnw -q -DskipTests compile
```

Useful commands during development:

```bash
./mvnw -q test
./mvnw -B verify
./mvnw -B -DskipTests checkstyle:check
./mvnw -B package
```

Target one module and its reactor dependencies during a tight feedback loop:

```bash
./mvnw -B -ntp -pl serverfeatures-api -am test
./mvnw -B -ntp -pl serverfeatures-platform-paper -am test
```

## Recommended Workflow

1. Create a branch for one focused change.
2. Implement behavior and tests in the same pass.
3. Run local validation (`test` at minimum).
4. Update docs when behavior or operator workflow changes.
5. Open a PR with context and operational impact.

## Engineering Guidelines

- Keep feature boundaries clean; avoid unnecessary cross-feature coupling.
- Prefer typed config access (`ConfigView` / `ConfigNode`) over raw casts.
- Make external calls fail-safe and time-bounded.
- Ensure disable/reload paths release resources cleanly.
- Keep logic testable; avoid burying behavior in hard-to-reach static paths.
- Keep public code in `serverfeatures-api` and Bukkit implementation/feature details in
  `serverfeatures-platform-paper`.
- Do not make the API depend on the plugin module. Introduce a narrow capability interface when reusable components
  require runtime services.

## Feature Authoring Checklist

When adding a new feature module:

1. Implement metadata in `features/<feature>/meta/Meta`.
2. Accept `FeatureContext<Meta>` in the feature constructor and pass it to `BukkitBaseFeature`.
3. Define `getDefaultConfig()` / `getDefaultMessages()`; defaults are written to the feature-owned files.
4. Register listeners, tasks, commands, APIs, data, caches, and GUIs through lifecycle managers.
5. Initialize already-online players when the feature normally initializes state in a join listener.
6. Implement `StatefulFeature<S>` when transient state must survive a full feature reload.
7. Add feature tests under the mirrored `src/test/java/...` package path.
8. Validate enable/disable/reload behavior with no leaked resources or command aliases.

## Before You Open a PR

- Build succeeds locally.
- Relevant tests pass.
- New behavior is covered by tests.
- Operationally important failures are logged clearly.
- `./mvnw -B -ntp verify` passes from a clean checkout.
- Public API changes include compatibility notes.
