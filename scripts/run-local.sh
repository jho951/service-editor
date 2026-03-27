#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

PROFILE="${1:-dev}"
shift || true

case "${PROFILE}" in
  dev|prod)
    ;;
  *)
    echo "지원하지 않는 프로필입니다: ${PROFILE}"
    echo "Usage: $0 [dev|prod] [추가 gradle 인자...]"
    exit 1
    ;;
esac

cd "${REPO_ROOT}"
./gradlew :documents-boot:bootRun --args="--spring.profiles.active=${PROFILE}" "$@"
