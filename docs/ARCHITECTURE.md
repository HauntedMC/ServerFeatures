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

1. Initializes shared config/localization handlers and the feature scope factories.
2. Discovers available feature classes through package scanning.
3. Resolves metadata and dependency requirements.
4. Prunes features with unresolved dependencies.
5. Prepares feature-owned config and message files.
6. Loads enabled features in dependency-safe order.

Each feature instance receives an immutable `FeatureContext`. The context bundles its metadata, scoped config,
scoped localization, logger, and a fresh lifecycle manager. Config/localization/logger scopes remain stable across
reloads, while listeners, tasks, commands, APIs, data connections, caches, and GUIs belong to exactly one loaded
feature instance.

During runtime, each feature extends `BukkitBaseFeature` and uses its `FeatureLifecycleManager` services for:

- listener registration
- scheduled tasks
- command registration
- feature API publication and cleanup
- optional data access (`DataProvider`)
- cache and GUI lifecycle

On disable/reload, cleanup attempts every lifecycle step even if an earlier step fails. Features implementing
`StatefulFeature` can snapshot transient runtime state before teardown and restore it after the replacement instance
initializes. Features with player-local runtime state also initialize players who are already online, so a feature
reload behaves like a clean startup rather than waiting for the next join.

Published feature APIs are registered in an in-process, ownership-aware catalog and, when available, DataRegistry's
shared catalog. This keeps feature-to-feature APIs available without making DataRegistry mandatory and still permits
cross-plugin discovery.

A dependency reload is a cascade: all snapshots are captured before mutation, dependents are torn down before their
dependencies, and replacements load in dependency order. A failed replacement never leaves a dependent registered
against a missing dependency.

## Configuration and Data

- `config.yml` stores shared/global settings only.
- Each feature owns `features/<FeatureName>/config.yml`.
- Framework messages live in `lang/messages*.yml`.
- Each feature owns `features/<FeatureName>/messages*.yml`.
- Feature defaults are injected into the feature's file and incompatible configured value types are reconciled per
  schema.
- Feature-local config files live in `local/*.yml`.
- YAML writes use same-directory atomic replacement where supported.

### Choosing persistence

Player `PersistentDataContainer` storage is the default and recommended persistence mechanism for small,
non-critical, local gameplay preferences such as personal toggles. Use it when the data:

- belongs to one player on one Paper server;
- can be reset or lost without meaningful operational, economic, moderation, or progression consequences;
- does not need queries, reporting, offline administration, relational constraints, audit history, or transactions;
- does not need synchronization or conflict resolution across backend servers.

Store a namespaced, typed value on the owning player and treat missing or malformed values as a safe default. Keep a
small in-memory view only when the hot path benefits from it; update PDC when the preference changes and let Paper's
normal playerdata lifecycle flush it. Document the key, type, default behavior, crash-loss window, and the fact that
server switches do not synchronize the value.

Use DataProvider/database persistence only when the feature has an explicit durability, querying, auditing,
transactional, offline-access, or cross-server consistency requirement. PDC is not appropriate for critical data
such as balances, sanctions, entitlements, claims, or authoritative progression.

## Why This Matters

For operators, this architecture means safer rollout and easier troubleshooting.

For contributors, it means clear ownership boundaries: keep feature logic inside the feature, and keep shared behavior in the framework.
