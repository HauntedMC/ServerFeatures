# ServerFeatures

[![CI Lint](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/ServerFeatures/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/ServerFeatures?sort=semver)](https://github.com/HauntedMC/ServerFeatures/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/ServerFeatures)](LICENSE)

One modular feature framework for your Paper server.

## Quick Start

1. Place `ServerFeatures.jar` in your Paper `plugins/` directory.
2. Install optional dependencies required by the features you plan to run.
3. Start the server once to generate runtime config files.
4. Enable the features you want in `plugins/ServerFeatures/config.yml`.
5. Use `/serverfeatures list` and `/serverfeatures info <feature>` to verify state.

## Requirements

- Java 25
- Paper `26.1.2+` (resolved via `paper-api` range)
- Feature-dependent optional plugins:
  - `DataRegistry`
  - `DataProvider`
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
mvn -B package
```

Output jar: `target/ServerFeatures.jar`

## Learn More

- [Configuration Guide](docs/CONFIGURATION.md)
- [Documentation Index](docs/README.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Testing and Quality](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
