# Migrating to ServerFeatures 3.0

Version 3.0 separates the reusable Paper API from the plugin runtime and narrows the GUI boundary. The installed
plugin and its runtime identity remain unchanged.

## Server operators

- Replace the existing plugin with `ServerFeatures.jar`; its filename, plugin name, main class, and configuration
  identity are unchanged.
- Use Java 25 and the pinned Paper version listed in the project README.
- Back up configuration and data, then verify the features and optional integrations used by your server before
  rollout.
- Feature sections under `config.yml -> features.*` migrate to
  `features/<FeatureName>/config.yml`. Feature messages migrate from `lang/messages*.yml` into the same feature
  directory. The root `config.yml` remains the home for `global.*` settings. Existing target values take precedence,
  and legacy source sections are removed only after their destination files are written successfully.

## Maven consumers

The historical runtime coordinate remains available:

```text
nl.hauntedmc.serverfeatures:serverfeatures:3.0.0
```

New integrations should use
`nl.hauntedmc.serverfeatures:serverfeatures-api:3.0.0` with `provided` scope. Do not use
`serverfeatures-platform-paper` as an artifact id; that is the repository module directory, while the published
runtime artifact retains the `serverfeatures` artifact id.

ServerFeatures now consumes
`nl.hauntedmc.proxyfeatures:proxyfeatures-contracts:3.0.0` instead of the full ProxyFeatures runtime. Publish or make
that contracts artifact available before building or releasing ServerFeatures 3.0.

## Source compatibility

- Custom menus now depend on `MenuNavigator`; components that also schedule GUI work use `MenuRuntime`.
- `FeatureGUIManager` implements `MenuRuntime`, so normal plugin code can continue passing that manager.
- Command relay, staff chat, vanish state, and vote messages now use the shared
  `nl.hauntedmc.proxyfeatures.contracts.messaging` schemas. The contracts fix each message type instead of accepting
  a caller-supplied type string.
- Repository source paths moved from the root `src/` tree into `serverfeatures-api` and
  `serverfeatures-platform-paper`.
- Feature constructors now receive `FeatureContext<Meta>` instead of the plugin plus separately constructed
  framework handlers.
- Features can publish lifecycle-owned APIs through `FeatureApiManager` and retain transient reload state through
  `StatefulFeature`.
- Build output moved from `target/ServerFeatures.jar` to
  `serverfeatures-platform-paper/target/ServerFeatures.jar`.

Recompile integrations against 3.0; do not assume binaries compiled against 2.x remain compatible.
