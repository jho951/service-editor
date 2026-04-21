#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_PROJECT_NAME="editor-service"
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

resolve_env_file() {
  local preferred="$PROJECT_ROOT/.env.$ENV_NAME"
  if [[ -f "$preferred" ]]; then
    echo "$preferred"
    return 0
  fi

  local fallback="$PROJECT_ROOT/.env.example"
  if [[ -f "$fallback" ]]; then
    echo "$fallback"
    return 0
  fi

  echo ""
}

gradle_property() {
  local key="$1"
  local gradle_properties="${HOME}/.gradle/gradle.properties"
  [[ -f "$gradle_properties" ]] || return 0
  awk -F= -v key="$key" '$1 == key { print $2; exit }' "$gradle_properties"
}

if [[ -z "${GH_TOKEN:-}" ]]; then
  GH_TOKEN="$(gradle_property githubPackagesToken)"
  [[ -n "$GH_TOKEN" ]] || GH_TOKEN="$(gradle_property githubToken)"
  [[ -n "$GH_TOKEN" ]] || GH_TOKEN="$(gradle_property ghToken)"
  [[ -n "$GH_TOKEN" ]] || GH_TOKEN="$(gradle_property gh_token)"
  export GH_TOKEN
fi

if [[ -z "${GITHUB_TOKEN:-}" && -n "${GH_TOKEN:-}" ]]; then
  export GITHUB_TOKEN="$GH_TOKEN"
fi

if [[ -z "${GITHUB_ACTOR:-}" ]]; then
  GITHUB_ACTOR="$(gradle_property githubPackagesUsername)"
  [[ -n "$GITHUB_ACTOR" ]] || GITHUB_ACTOR="$(gradle_property githubUsername)"
  [[ -n "$GITHUB_ACTOR" ]] || GITHUB_ACTOR="jho951"
  export GITHUB_ACTOR
fi

SHARED_NETWORK="${SERVICE_SHARED_NETWORK:-service-backbone-shared}"
if ! docker network inspect "$SHARED_NETWORK" >/dev/null 2>&1; then
  echo "Creating external network: $SHARED_NETWORK"
  docker network create "$SHARED_NETWORK" >/dev/null
fi

compose() {
  local env_file
  env_file="$(resolve_env_file)"
  if [[ -n "$env_file" ]]; then
    SERVICE_SHARED_NETWORK="$SHARED_NETWORK" EDITOR_ENV_FILE="$env_file" \
      docker compose --env-file "$env_file" -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
    return
  fi

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
