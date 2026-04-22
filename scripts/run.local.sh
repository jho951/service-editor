#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROFILE="${1:-dev}"
shift || true
ENV_FILE="${PROJECT_ROOT}/.env.local"

if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${PROJECT_ROOT}/.env.example"
fi

case "${PROFILE}" in
  dev|prod) ;;
  *)
    echo "Usage: ./scripts/run.local.sh [dev|prod] [extra gradle args...]" >&2
    exit 1
    ;;
esac

cd "${PROJECT_ROOT}"
if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi
./gradlew :documents-boot:bootRun --args="--spring.profiles.active=${PROFILE}" "$@"
