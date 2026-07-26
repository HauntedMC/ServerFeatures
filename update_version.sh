#!/usr/bin/env bash
set -euo pipefail

readonly POM_FILE="pom.xml"
readonly API_POM_FILE="serverfeatures-api/pom.xml"
readonly PLUGIN_POM_FILE="serverfeatures-platform-paper/pom.xml"
readonly PLUGIN_YML_FILE="serverfeatures-platform-paper/src/main/resources/plugin.yml"
readonly VERSION_PROPERTY="revision"
readonly VERSIONS_PLUGIN="org.codehaus.mojo:versions-maven-plugin:2.18.0"

die() {
  echo "Error: $*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage: ./update_version.sh <major|minor|patch>

Bumps the reactor revision, verifies filtered Paper metadata, then creates a local release commit and tag.
USAGE
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || die "${path} not found."
}

resolve_version() {
  local module="${1:-}"
  local -a module_args=()
  local version
  if [[ -n "$module" ]]; then
    module_args=(-pl "$module")
  fi
  version="$(
    ./mvnw -q -ntp "${module_args[@]}" -DforceStdout help:evaluate -Dexpression=project.version \
      | awk '/^[0-9]+\.[0-9]+\.[0-9]+$/ { print; exit }'
  )"
  [[ -n "$version" ]] || die "Unable to resolve a semantic Maven version${module:+ for ${module}}."
  echo "$version"
}

bump_semver() {
  local semver="$1"
  local bump_type="$2"
  local major minor patch
  [[ "$semver" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] \
    || die "Current version must be semantic (X.Y.Z), got '${semver}'."
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  case "$bump_type" in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
    *) usage; exit 1 ;;
  esac
  echo "${major}.${minor}.${patch}"
}

if [[ $# -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
  usage
  exit 0
fi
[[ $# -eq 1 ]] || { usage; exit 1; }
[[ "$1" == "major" || "$1" == "minor" || "$1" == "patch" ]] || { usage; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Run this script inside the repository."

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"
for required in "$POM_FILE" "$API_POM_FILE" "$PLUGIN_POM_FILE" "$PLUGIN_YML_FILE" "mvnw"; do
  require_file "$required"
done
[[ -z "$(git status --porcelain)" ]] || die "Working tree is not clean. Commit or stash changes first."
grep -Fq "version: \${project.version}" "$PLUGIN_YML_FILE" \
  || die "${PLUGIN_YML_FILE} must use the Maven version placeholder."

current_version="$(resolve_version)"
new_version="$(bump_semver "$current_version" "$1")"
new_tag="v${new_version}"
git rev-parse -q --verify "refs/tags/${new_tag}" >/dev/null 2>&1 && die "Tag ${new_tag} already exists."

echo "Current version: ${current_version}"
echo "Bumping to: ${new_version}"
./mvnw -B -ntp "${VERSIONS_PLUGIN}:set-property" \
  -Dproperty="${VERSION_PROPERTY}" -DnewVersion="${new_version}" -DgenerateBackupPoms=false

for module in "" serverfeatures-testkit serverfeatures-api serverfeatures-platform-paper; do
  resolved="$(resolve_version "$module")"
  [[ "$resolved" == "$new_version" ]] || die "Resolved ${module:-reactor} version '${resolved}', expected '${new_version}'."
done

git add "$POM_FILE"
git commit -m "Bump version to ${new_tag} for release"
git tag --annotate "$new_tag" --message "Release ${new_tag}"

echo "Version updated locally."
echo "Next step: git push origin HEAD && git push origin ${new_tag}"
