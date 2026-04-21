#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROFILE="${1:-dev}"
shift || true

case "${PROFILE}" in
  dev|prod) ;;
  *)
    echo "Usage: ./scripts/run.local.sh [dev|prod] [extra gradle args...]" >&2
    exit 1
    ;;
esac

cd "${PROJECT_ROOT}"
./gradlew :documents-boot:bootRun --args="--spring.profiles.active=${PROFILE}" "$@"
