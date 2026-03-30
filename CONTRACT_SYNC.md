# Contract Sync (Block-server)

- Repo: `https://github.com/jho951/Block-server`
- Service SoT Branch: `dev`
- Contract Role: Document/workspace domain API owner

## Contract Source
- Contract Repo: `https://github.com/jho951/contract`
- Contract Commit SHA: `79dcbadd3428749cd2f4d0615f8443bdfe8aae5a`
- Latest Sync Date: `2026-03-31`

## Required Links
- Routing: https://github.com/jho951/contract/blob/main/contracts/routing.md
- Headers: https://github.com/jho951/contract/blob/main/contracts/headers.md
- Security: https://github.com/jho951/contract/blob/main/contracts/security.md
- Errors: https://github.com/jho951/contract/blob/main/contracts/errors.md
- Env: https://github.com/jho951/contract/blob/main/contracts/env.md

## Impact Scope
- Contract Areas:
  - `env`
- Affected Endpoints or Flows:
  - `docker compose runtime network configuration`
  - `service-backbone-shared` network alias `documents-service`

## Sync Checklist
- [x] gateway upstream DNS/path assumptions match contract
- [x] trace header usage matches contract
- [x] protected/admin route expectations match contract
- [x] docker runtime networking reflects env contract

## Notes
- `docker/docker-compose.yml`
- `docker/docker-compose.dev.yml`
- `docker/docker-compose.prod.yml`
- Host port publish를 제거하고 private/shared 네트워크 분리를 유지한다.
