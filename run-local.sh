#!/usr/bin/env bash
set -euo pipefail

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

./gradlew :documents-boot:bootRun --args="--spring.profiles.active=${PROFILE}" "$@"
