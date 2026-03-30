#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_DIR="${REPO_ROOT}/docker"
MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK:-msa-shared}"

ENV_NAME="dev"
ACTION="up"

if [[ $# -eq 1 ]]; then
  case "$1" in
    dev|prod)
      ENV_NAME="$1"
      ;;
    all|build|up|down|logs|restart|nuke|ps)
      ACTION="$1"
      ;;
    *)
      echo "지원하지 않는 인자입니다: $1"
      echo "Usage: $0 [dev|prod] [all|build|up|down|logs|restart|nuke|ps]"
      exit 1
      ;;
  esac
elif [[ $# -ge 2 ]]; then
  ENV_NAME="$1"
  ACTION="$2"
fi

case "${ENV_NAME}" in
  dev)
    COMPOSE_FILE="${DOCKER_DIR}/docker-compose.dev.yml"
    APP_CONTAINER="documents-app-dev"
    ;;
  prod)
    COMPOSE_FILE="${DOCKER_DIR}/docker-compose.prod.yml"
    APP_CONTAINER="documents-app-prod"
    ;;
  *)
    echo "지원하지 않는 환경입니다: ${ENV_NAME}"
    echo "Usage: $0 [dev|prod] [all|build|up|down|logs|restart|nuke|ps]"
    exit 1
    ;;
esac

docker network inspect "${MSA_SHARED_NETWORK}" >/dev/null 2>&1 || docker network create "${MSA_SHARED_NETWORK}" >/dev/null

case "${ACTION}" in
  all)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" build --no-cache
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" up -d
    docker logs "${APP_CONTAINER}" --tail=200 -f
    ;;
  build)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" build
    ;;
  up)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" up -d
    ;;
  down)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" down
    ;;
  logs)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" logs -f
    ;;
  restart)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" down
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" up -d
    ;;
  nuke)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" down -v
    docker image prune -f
    ;;
  ps)
    MSA_SHARED_NETWORK="${MSA_SHARED_NETWORK}" docker compose -f "${COMPOSE_FILE}" ps
    ;;
  *)
    echo "지원하지 않는 동작입니다: ${ACTION}"
    echo "Usage: $0 [dev|prod] [all|build|up|down|logs|restart|nuke|ps]"
    exit 1
    ;;
esac
