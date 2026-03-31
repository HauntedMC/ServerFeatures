#!/usr/bin/env bash
set -euo pipefail

PLUGIN_YML_FILE="src/main/resources/plugin.yml"
PLUGIN_VERSION_PLACEHOLDER='${project.version}'

usage() {
  echo "Usage: $0 <major|minor|patch>" >&2
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "This script must be run inside a git repository." >&2
  exit 1
fi

bump_type="$1"
if [[ "$bump_type" != "major" && "$bump_type" != "minor" && "$bump_type" != "patch" ]]; then
  usage
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven (mvn) is required but was not found in PATH." >&2
  exit 1
fi

if [[ ! -f pom.xml ]]; then
  echo "pom.xml not found." >&2
  exit 1
fi

if [[ ! -f "$PLUGIN_YML_FILE" ]]; then
  echo "$PLUGIN_YML_FILE not found." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes before bumping version." >&2
  exit 1
fi

current_version="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version | tr -d '\r')"
if [[ ! "$current_version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Project version must be a semantic version like 1.2.3." >&2
  exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"

plugin_version="$(
  sed -nE 's/^[[:space:]]*version:[[:space:]]*([^[:space:]]+)[[:space:]]*$/\1/p' "$PLUGIN_YML_FILE" | head -n 1
)"
if [[ -z "$plugin_version" ]]; then
  echo "Could not parse plugin version from $PLUGIN_YML_FILE." >&2
  exit 1
fi

if [[ "$plugin_version" != "$PLUGIN_VERSION_PLACEHOLDER" && ! "$plugin_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "plugin.yml version must be \${project.version} or a semantic version like 1.2.3." >&2
  exit 1
fi

if [[ "$plugin_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$plugin_version" != "$current_version" ]]; then
  echo "Version mismatch: pom.xml has $current_version but $PLUGIN_YML_FILE has $plugin_version." >&2
  exit 1
fi

case "$bump_type" in
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  patch)
    patch=$((patch + 1))
    ;;
esac

new_version="${major}.${minor}.${patch}"
new_tag="v${new_version}"

if git rev-parse -q --verify "refs/tags/${new_tag}" >/dev/null 2>&1; then
  echo "Tag ${new_tag} already exists." >&2
  exit 1
fi

echo "New version: $new_tag"

mvn -q versions:set -DnewVersion="$new_version" -DgenerateBackupPoms=false

if [[ "$plugin_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  sed -i -E "s/^([[:space:]]*version:[[:space:]]*)[0-9]+\.[0-9]+\.[0-9]+([[:space:]]*)$/\1${new_version}\2/" "$PLUGIN_YML_FILE"
  git add pom.xml "$PLUGIN_YML_FILE"
else
  git add pom.xml
fi

git commit -m "Bump version to $new_tag for release"
git tag "$new_tag"

echo "Version updated locally. Push the branch and tag when ready:"
echo "  git push && git push origin $new_tag"
