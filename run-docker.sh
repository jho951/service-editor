#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/docker"

ENV_NAME="${1:-}"
ACTION="${2:-}"

if [[ -z "${ENV_NAME}" || -z "${ACTION}" ]]; then
  echo "Usage: $0 {dev|prod} {all|build|up|down|logs|restart|nuke|ps}"
  exit 1
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
    echo "Usage: $0 {dev|prod} {all|build|up|down|logs|restart|nuke|ps}"
    exit 1
    ;;
esac

case "${ACTION}" in
  all)
    docker compose -f "${COMPOSE_FILE}" build --no-cache
    docker compose -f "${COMPOSE_FILE}" up -d
    docker logs "${APP_CONTAINER}" --tail=200 -f
    ;;
  build)
    docker compose -f "${COMPOSE_FILE}" build
    ;;
  up)
    docker compose -f "${COMPOSE_FILE}" up -d
    ;;
  down)
    docker compose -f "${COMPOSE_FILE}" down
    ;;
  logs)
    docker compose -f "${COMPOSE_FILE}" logs -f
    ;;
  restart)
    docker compose -f "${COMPOSE_FILE}" down
    docker compose -f "${COMPOSE_FILE}" up -d
    ;;
  nuke)
    docker compose -f "${COMPOSE_FILE}" down -v
    docker image prune -f
    ;;
  ps)
    docker compose -f "${COMPOSE_FILE}" ps
    ;;
  *)
    echo "지원하지 않는 동작입니다: ${ACTION}"
    echo "Usage: $0 {dev|prod} {all|build|up|down|logs|restart|nuke|ps}"
    exit 1
    ;;
esac
