#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ $# -eq 1 ]]; then
  exec "${REPO_ROOT}/run-docker.sh" dev "$1"
fi

exec "${REPO_ROOT}/run-docker.sh" "$@"
