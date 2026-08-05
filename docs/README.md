# ServerFeatures Docs

This folder is the practical guide for running, maintaining, and contributing to ServerFeatures.

## Start Here

If you run the plugin:

- [Feature reference](features/README.md): commands, permissions, configuration, placeholders, runtime behavior, and troubleshooting for every Paper feature.
- [Configuration](CONFIGURATION.md): day-to-day setup and safe change workflow.
- [Architecture](ARCHITECTURE.md): how feature discovery, dependency resolution, and lifecycle cleanup work.

If you contribute code:

- [Development](DEVELOPMENT.md): local setup and coding workflow.
- [Testing](TESTING.md): test strategy and local validation commands.
- [Release process](RELEASE.md): versioning, verification, publication, and artifacts.
- [Contributing Guide](../CONTRIBUTING.md): pull request expectations.

## Documentation rule

Every feature package has one matching page in `docs/features/`. Change that page in the same pull request whenever the feature's commands, permissions, configuration, placeholders, message variables, integrations, persistence, or lifecycle behavior changes.
