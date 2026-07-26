# Release Process

## 1. Prepare

- Publish the matching ProxyFeatures release first when `proxyfeatures.version` changes; ServerFeatures consumes its
  `proxyfeatures-contracts` Maven artifact.
- Work from a clean `main` branch and confirm CI is green.
- Review compatibility and operator-facing changes.
- Run the complete local gate:

```bash
./mvnw -B -ntp -Pplatform-acceptance verify
```

The acceptance profile requires Docker and boots the exact packaged jar on the pinned, checksum-verified Paper
runtime.

## 2. Bump and Tag

```bash
./update_version.sh major
./update_version.sh minor
./update_version.sh patch
```

Choose one command. The script requires a clean worktree, updates the reactor `revision`, validates filtered Paper
metadata, checks every public/runtime module resolves the same semantic version, then creates a local commit and
annotated `vX.Y.Z` tag. Review the commit before pushing:

```bash
git push origin HEAD
git push origin vX.Y.Z
```

## 3. Automated Release

`.github/workflows/release-package.yml`:

1. Rejects malformed tags and any tag that differs from the Maven project version.
2. Runs one `deploy` reactor with the `release` and `platform-acceptance` profiles.
3. Enforces Java/Maven versions, pinned plugins, dependency convergence and upper bounds, banned legacy Adventure
   modules, direct dependency declarations, duplicate classes, Checkstyle, tests, coverage, javadocs, distribution
   contents, and the real Paper boot gate.
4. Uses Maven `deployAtEnd`, so deployment does not begin until every reactor build and verification gate succeeds.
5. Publishes the parent, API, and plugin artifacts with sources and javadocs. The internal testkit and acceptance
   fixtures are explicitly excluded from deployment.
6. Generates a reproducible CycloneDX SBOM and uploads it, the distributable jar, and SHA-256 checksums to the GitHub
   Release.

Build timestamps are pinned through `project.build.outputTimestamp` to make equivalent source builds reproducible.
The Maven Wrapper distribution and third-party GitHub Actions are checksum/SHA pinned.

## 4. Published Coordinates

- Repository: `https://maven.pkg.github.com/HauntedMC/ServerFeatures`
- Group: `nl.hauntedmc.serverfeatures`
- Public artifact: `serverfeatures-api`
- Runtime artifact: `serverfeatures`
