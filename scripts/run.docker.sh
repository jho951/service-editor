#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT_NAME="documents-service"
ACTION="${1:-up}"
ENV_NAME="${2:-dev}"
shift $(( $# > 0 ? 1 : 0 )) || true
shift $(( $# > 0 ? 1 : 0 )) || true

usage() {
  echo "Usage: ./scripts/run.docker.sh [up|down|build|logs|ps|restart|nuke] [dev|prod] [docker compose options]" >&2
}

case "$ACTION" in
  up|down|build|logs|ps|restart|nuke) ;;
  *) usage; exit 1 ;;
esac

case "$ENV_NAME" in
  dev|prod) COMPOSE_FILE="$PROJECT_ROOT/docker/$ENV_NAME/compose.yml" ;;
  *) usage; exit 1 ;;
esac

SHARED_NETWORK="${SERVICE_SHARED_NETWORK:-service-backbone-shared}"
if ! docker network inspect "$SHARED_NETWORK" >/dev/null 2>&1; then
  echo "Creating external network: $SHARED_NETWORK"
  docker network create "$SHARED_NETWORK" >/dev/null
fi

compose() {
  SERVICE_SHARED_NETWORK="$SHARED_NETWORK" docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

case "$ACTION" in
  up) compose up -d --build "$@" ;;
  down) compose down --remove-orphans "$@" ;;
  build) compose build "$@" ;;
  logs) compose logs -f "$@" ;;
  ps) compose ps "$@" ;;
  restart) compose down --remove-orphans && compose up -d --build "$@" ;;
  nuke) compose down --remove-orphans -v "$@" ;;
esac
