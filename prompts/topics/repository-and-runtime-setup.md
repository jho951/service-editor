# Repository And Runtime Setup

## 목적

멀티 모듈 전환, 런타임 설정, 빌드 구성, 초기 부트스트랩, Docker 실행 스크립트 정리 흐름을 다시 찾기 쉽게 묶는다.

## 관련 worklog

- [2026-03-13-multi-module-migration.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-13-multi-module-migration.md)
- [2026-03-14-gitignore-secrets.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-14-gitignore-secrets.md)
- [2026-03-14-gradle-properties-runtime-config.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-14-gradle-properties-runtime-config.md)
- [2026-03-14-jpa-migration.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-14-jpa-migration.md)
- [2026-03-14-module-name-sync.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-14-module-name-sync.md)
- [2026-03-15-gradle-version-properties.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-15-gradle-version-properties.md)
- [2026-03-15-workspace-bootstrap.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-15-workspace-bootstrap.md)
- [2026-03-16-application-runtime-config.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-16-application-runtime-config.md)
- [2026-03-27-docker-env-split-and-run-scripts.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-docker-env-split-and-run-scripts.md)

## 관련 문서

- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [docs/runbook/DEBUG.md](https://github.com/jho951/Block-server/blob/dev/docs/runbook/DEBUG.md)
- [docs/decisions/019-isolate-docker-runtime-on-private-and-shared-networks.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/019-isolate-docker-runtime-on-private-and-shared-networks.md)

## 현재 기준

- 저장소 구조와 런타임 설정은 멀티 모듈 구조와 분리된 실행 환경 기준으로 본다.
- Docker 실행 흐름과 local 실행 흐름은 별도 스크립트와 환경 파일 정책을 함께 본다.

## 열어둘 질문

- contract sync 기록 운영을 더 일반화할지 여부
- prod 실행 스크립트와 로컬 개발 스크립트의 공통화 범위를 더 넓힐지 여부
