#!/usr/bin/env bash
set -euo pipefail

work_directory="${PLATFORM_ACCEPTANCE_WORK_DIRECTORY:?PLATFORM_ACCEPTANCE_WORK_DIRECTORY must be set}"
paper_log="$work_directory/paper/paper.log"

[[ -f "$paper_log" ]] || {
    echo "ServerFeatures acceptance failure: Paper log is missing: $paper_log" >&2
    exit 1
}

grep -Eq 'AutoPickup loaded with persistent direct-drop collection\.' "$paper_log" || {
    echo "ServerFeatures acceptance failure: AutoPickup did not complete initialization." >&2
    exit 1
}

if grep -Eq "Failed to close DataProvider scope for feature 'AutoPickup'|Rejected DataProvider .*inactive plugin identity" "$paper_log"; then
    echo "ServerFeatures acceptance failure: AutoPickup DataProvider cleanup was rejected." >&2
    grep -En "Failed to close DataProvider scope for feature 'AutoPickup'|Rejected DataProvider .*inactive plugin identity" "$paper_log" >&2 || true
    exit 1
fi
