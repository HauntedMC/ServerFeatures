# ServerFeatures

[![CI Lint](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/ServerFeatures?sort=semver)](https://github.com/HauntedMC/ServerFeatures/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/ServerFeatures)](LICENSE)

A modular feature framework and reusable API for your Paper server.

## Quick Start

1. Place `ServerFeatures.jar` in your Paper `plugins/` directory.
2. Install optional dependencies required by the features you plan to run.
3. Start the server once to generate runtime config files.
4. Enable the features you want in `plugins/ServerFeatures/config.yml`.
5. Use `/serverfeatures list` and `/serverfeatures info <feature>` to verify state.

## Requirements

- Java 25
- Paper `26.2`
- Feature-dependent optional plugins:
  - `DataRegistry` `1.13.4`
  - `DataProvider` `3.1.8`
  - `packetevents`
  - `PlaceholderAPI`
  - other integrations (for example Vault, LuckPerms, WorldGuard) only when using related features

## Build From Source

Add GitHub Packages credentials for Maven server id `github` in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN</password>
    </server>
  </servers>
</settings>
```

Use a token with `read:packages` (and `repo` if package source repositories are private), then run:

```bash
./mvnw -B -ntp verify
```

Output jar: `serverfeatures-platform-paper/target/ServerFeatures.jar`

Run the real Paper acceptance gate (Docker required) with:

```bash
./mvnw -B -ntp -Pplatform-acceptance verify
```

It boots the packaged ServerFeatures jar with DataProvider and DataRegistry, verifies their public runtime integration,
and requires a clean shutdown. Set `PLATFORM_ACCEPTANCE_KEEP_WORK_DIRECTORY=true` to retain logs.

## Published Modules

- `serverfeatures-api`: reusable feature, command, configuration, UI, and utility contracts.
- `proxyfeatures-contracts`: shared persistence and Redis wire schemas consumed with `provided` scope.
- `serverfeatures`: the installable Paper plugin; its jar keeps the historical `ServerFeatures.jar` name.

The testkit is reactor-internal and is not part of the supported production API. Maven consumers should use the API
artifact with `provided` scope when ServerFeatures supplies it at runtime.

```xml
<dependency>
  <groupId>nl.hauntedmc.serverfeatures</groupId>
  <artifactId>serverfeatures-api</artifactId>
  <version>3.0.0</version>
  <scope>provided</scope>
</dependency>
```

## Repository Layout

- `serverfeatures-api`: public contracts and reusable Paper-facing components.
- `serverfeatures-testkit`: common test infrastructure.
- `serverfeatures-platform-paper`: lifecycle framework, concrete features, and distributable jar.
- `serverfeatures-platform-acceptance`: API-only consumer and real Paper boot gate.

## Learn More

- [Configuration Guide](docs/CONFIGURATION.md)
- [Documentation Index](docs/README.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Testing and Quality](docs/TESTING.md)
- [Release Process](docs/RELEASE.md)
- [Migrating to 3.0](docs/MIGRATING-3.0.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
