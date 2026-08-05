#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${PLATFORM_ACCEPTANCE_WORK_DIRECTORY:-}" ]]; then
    echo "AutoPickup shutdown log assertion skipped: no retained platform acceptance directory was configured."
    exit 0
fi

paper_log="$PLATFORM_ACCEPTANCE_WORK_DIRECTORY/paper/paper.log"

[[ -f "$paper_log" ]] || {
    echo "ServerFeatures acceptance failure: Paper log is missing: $paper_log" >&2
    exit 1
}

grep -Eq 'AutoPickup loaded with local PDC-persisted direct-drop collection\.' "$paper_log" || {
    echo "ServerFeatures acceptance failure: AutoPickup did not complete initialization." >&2
    exit 1
}
