# ServerFeatures Docs

This folder is the practical guide for running, maintaining, and contributing to ServerFeatures.

## Start Here

If you run the plugin:

- [Configuration](CONFIGURATION.md): day-to-day setup and safe change workflow.
- [Architecture](ARCHITECTURE.md): how feature discovery, dependency resolution, and lifecycle cleanup work.

If you contribute code:

- [Development](DEVELOPMENT.md): local setup and coding workflow.
- [Testing](TESTING.md): test strategy and local validation commands.
- [Contributing Guide](../CONTRIBUTING.md): pull request expectations.

## Release Notes

Releases are tag-driven (`v*` tags trigger packaging and publication).

Typical flow:

1. Ensure CI is green on your target branch.
2. Bump version and create a release tag.
3. Push branch and tag, then monitor the release workflow.
