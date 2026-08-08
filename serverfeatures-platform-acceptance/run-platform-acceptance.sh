#!/usr/bin/env bash
set -euo pipefail

root_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
acceptance_directory="$root_directory/serverfeatures-platform-acceptance"
compose_file="$acceptance_directory/docker-compose.yml"
if [[ -n "${PLATFORM_ACCEPTANCE_WORK_DIRECTORY:-}" ]]; then
    work_directory="$PLATFORM_ACCEPTANCE_WORK_DIRECTORY"
    created_work_directory=false
else
    work_directory="$(mktemp -d)"
    created_work_directory=true
fi
keep_work_directory="${PLATFORM_ACCEPTANCE_KEEP_WORK_DIRECTORY:-false}"

cleanup() {
    local exit_code=$?
    docker compose --file "$compose_file" logs --no-color >"$work_directory/backend.log" 2>&1 || true
    docker compose --file "$compose_file" down --volumes --timeout 10 >/dev/null 2>&1 || true
    if [[ $exit_code -ne 0 ]]; then find "$work_directory" -maxdepth 2 -name '*.log' -type f -print -exec tail -n 200 {} \; >&2 || true; fi
    if [[ "$keep_work_directory" == "true" || "$created_work_directory" != "true" ]]; then
        echo "Platform acceptance logs retained in $work_directory" >&2
    else
        rm -rf "$work_directory"
    fi
    exit "$exit_code"
}
trap cleanup EXIT

fail() { echo "ServerFeatures acceptance failure: $*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"; }
property() { sed -n "s|.*<$1>\\(.*\\)</$1>.*|\\1|p" "$root_directory/pom.xml" | head -n 1; }
wait_for_log() {
    local file=$1 expected=$2 deadline=$((SECONDS + 180))
    while (( SECONDS < deadline )); do
        grep -Eq -- "$expected" "$file" && return
        grep -Eq 'SERVERFEATURES_ACCEPTANCE_FAIL|Exception in thread|Could not load|Error occurred while enabling' "$file" && fail "Paper reported a boot failure."
        sleep 1
    done
    fail "Timed out waiting for $expected"
}

for command in curl docker java jq jar sha256sum; do require "$command"; done
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable."
mkdir -p \
    "$work_directory/paper/plugins/DataProvider/databases" \
    "$work_directory/paper/plugins/DataRegistry" \
    "$work_directory/paper/plugins/ServerFeatures/features/AutoPickup" \
    "$work_directory/paper/plugins/ServerFeatures/features/BuiltinCommandBlocker"

dataregistry_version="$(property dataregistry.version)"
dataprovider_version="$(property dataprovider.version)"
paper_version="$(property paper.runtime.version)"
paper_build="$(property paper.runtime.build)"
paper_sha256="$(property paper.runtime.sha256)"
repository="${MAVEN_REPO_LOCAL:?MAVEN_REPO_LOCAL must identify the Maven local repository}"
provider_bundle="$repository/nl/hauntedmc/dataprovider/dataprovider-platform-paper/$dataprovider_version/dataprovider-platform-paper-$dataprovider_version-bundled.jar"
registry_bundle="$repository/nl/hauntedmc/dataregistry/dataregistry-platform-paper/$dataregistry_version/dataregistry-platform-paper-$dataregistry_version-bundled.jar"
consumer="$acceptance_directory/consumer/target/serverfeatures-acceptance-consumer.jar"
plugin="$root_directory/serverfeatures-platform-paper/target/ServerFeatures.jar"
for artifact in "$plugin" "$provider_bundle" "$registry_bundle" "$consumer"; do [[ -f "$artifact" ]] || fail "Missing acceptance artifact $artifact"; done

metadata="$(curl --fail --silent --show-error --location "https://fill.papermc.io/v3/projects/paper/versions/$paper_version/builds")"
runtime_url="$(jq --raw-output --argjson build "$paper_build" '.[] | select(.id == $build) | .downloads["server:default"].url' <<<"$metadata")"
[[ "$runtime_url" != "null" ]] || fail "Paper runtime build is unavailable."
curl --fail --silent --show-error --location --output "$work_directory/paper.jar" "$runtime_url"
[[ "$(sha256sum "$work_directory/paper.jar" | awk '{print $1}')" == "$paper_sha256" ]] || fail "Paper runtime checksum mismatch."

cp "$provider_bundle" "$work_directory/paper/plugins/DataProvider.jar"
printf 'Multi-Release: true\n\n' >"$work_directory/data-provider-multi-release.mf"
jar --update --file "$work_directory/paper/plugins/DataProvider.jar" --manifest "$work_directory/data-provider-multi-release.mf" >/dev/null
cp "$registry_bundle" "$work_directory/paper/plugins/DataRegistry.jar"
cp "$plugin" "$work_directory/paper/plugins/ServerFeatures.jar"
cp "$consumer" "$work_directory/paper/plugins/ServerFeaturesAcceptance.jar"
printf '%s\n' 'orm:' '  schema_mode: update' 'databases:' '  mysql: { enabled: true }' '  mongodb: { enabled: false }' '  redis: { enabled: false }' '  redis_messaging: { enabled: false }' >"$work_directory/paper/plugins/DataProvider/config.yml"
printf '%s\n' 'enabled: true' >"$work_directory/paper/plugins/ServerFeatures/features/AutoPickup/config.yml"
printf '%s\n' 'enabled: true' 'remove_from_command_map: true' 'allowed:' '  - minecraft:stop' >"$work_directory/paper/plugins/ServerFeatures/features/BuiltinCommandBlocker/config.yml"
printf '%s\n' 'aliases:' '  version-alias:' '    - version' '  version-alias-chain:' '    - version-alias' >"$work_directory/paper/commands.yml"

docker compose --file "$compose_file" up --detach --wait
mysql_port="$(docker compose --file "$compose_file" port mysql 3306 | sed -n 's/.*://p' | head -n 1)"
[[ -n "$mysql_port" ]] || fail "Unable to resolve MySQL port."
printf '%s\n' 'player_data_rw:' '  access: { owner_plugin: "DataRegistry", shared_with: ["ServerFeatures"] }' '  host: 127.0.0.1' "  port: $mysql_port" '  database: minecraft' '  username: root' '  password: acceptance-root' '  ssl_mode: DISABLED' '  pool_size: 3' '  min_idle: 0' >"$work_directory/paper/plugins/DataProvider/databases/mysql.yml"
printf '%s\n' 'orm:' '  schema-mode: update' >"$work_directory/paper/plugins/DataRegistry/config.yml"
printf '%s\n' 'eula=true' >"$work_directory/paper/eula.txt"
printf '%s\n' 'server-port=0' >"$work_directory/paper/server.properties"

mkfifo "$work_directory/paper/console.in"
(cd "$work_directory/paper" && exec java -Xms512M -Xmx1G -jar "$work_directory/paper.jar" --nogui <console.in >paper.log 2>&1) &
paper_pid=$!
exec {paper_input_fd}>"$work_directory/paper/console.in"
wait_for_log "$work_directory/paper/paper.log" 'SERVERFEATURES_ACCEPTANCE_PASS platform=paper'
grep -Eq "Loaded feature 'AutoPickup'|Loaded feature AutoPickup|AutoPickup.*loaded" "$work_directory/paper/paper.log" \
    || fail "AutoPickup did not load during Paper acceptance."
grep -Eq "Feature loaded: BuiltinCommandBlocker|Loaded feature 'BuiltinCommandBlocker'|Loaded feature BuiltinCommandBlocker|BuiltinCommandBlocker.*loaded" "$work_directory/paper/paper.log" \
    || fail "BuiltinCommandBlocker did not load during Paper acceptance."
printf 'stop\n' >&"$paper_input_fd"
deadline=$((SECONDS + 45))
while kill -0 "$paper_pid" 2>/dev/null && (( SECONDS < deadline )); do sleep 1; done
kill -0 "$paper_pid" 2>/dev/null && fail "Paper did not stop cleanly."
wait "$paper_pid" || true
grep -Eq 'ServerFeatures is shutting down' "$work_directory/paper/paper.log" || fail "ServerFeatures did not shut down cleanly."
echo "ServerFeatures platform acceptance passed."
