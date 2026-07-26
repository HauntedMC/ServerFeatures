# Architecture Overview

ServerFeatures has two kinds of modularity: Maven modules enforce compile-time ownership, while runtime feature
classes can be enabled, disabled, and reloaded independently.

## Build Modules

```text
serverfeatures-testkit ──(test only)──▶ api / paper
serverfeatures-api ───────────────────▶ paper plugin
proxyfeatures-contracts ──────────────▶ paper plugin
paper plugin ─────────────────────────▶ packaged ServerFeatures.jar
```

- `serverfeatures-api` owns public feature, command, configuration, UI, and utility contracts. It may depend on Paper
  and supported integration APIs, but never on plugin framework or feature implementation packages.
- `serverfeatures-testkit` owns shared filesystem/proxy test helpers and is never a production dependency.
- `serverfeatures-platform-paper` owns the plugin bootstrap, lifecycle implementation, integrations, and concrete
  server features.
- The narrow `proxyfeatures-contracts` dependency shares sanction persistence types and cross-platform Redis message
  schemas without coupling ServerFeatures to the full ProxyFeatures plugin.
- `serverfeatures-platform-acceptance` is activated by a Maven profile. Its API-only consumer validates packaging,
  and its final module boots a pinned Paper runtime.

Dependencies point toward public contracts. Reusable API UI components use `MenuNavigator` and `MenuRuntime`
capabilities rather than importing `FeatureGUIManager`.

## Design Goals

- Keep feature implementations isolated to reduce cross-feature regression risk.
- Centralize lifecycle concerns (listeners, tasks, commands, caches, data access).
- Let operators roll out features incrementally instead of all at once.

## Runtime Model

At startup, the plugin:

1. Initializes shared config and localization handlers.
2. Discovers available feature classes through package scanning.
3. Resolves metadata and dependency requirements.
4. Prunes features with unresolved dependencies.
5. Loads enabled features in dependency-safe order.

During runtime, each feature extends `BukkitBaseFeature` and uses `FeatureLifecycleManager` services for:

- listener registration
- scheduled tasks
- command registration
- optional data access (`DataProvider`)
- cache and GUI lifecycle

On disable/reload, feature cleanup cancels tasks, unregisters listeners/commands, closes data connections, and clears cache/GUI state to avoid leaks.

## Configuration and Data

- `config.yml` is the primary control surface (`features.*` + global keys).
- Feature defaults are injected and unknown keys can be reconciled/cleaned per schema.
- Feature-local config files live in `local/*.yml`.
- Localization files live in `lang/*.yml`.

## Why This Matters

For operators, this architecture means safer rollout and easier troubleshooting.

For contributors, it means clear ownership boundaries: keep feature logic inside the feature, and keep shared behavior in the framework.
