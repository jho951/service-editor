# 2026-03-27 Docker Env Split And Run Scripts

- 목적: Docker 실행 환경을 dev/prod로 분리하고 실행 스크립트를 local/docker 2종으로 정리한다.
- Step 1: `docker/docker-compose.dev.yml`, `docker/docker-compose.prod.yml`를 추가해 환경별 compose를 분리했다.
- Step 2: 루트 `run-docker.sh`를 추가해 `dev|prod` + 액션(`all|build|up|down|logs|restart|nuke|ps`) 실행 구조를 만들었다.
- Step 3: 루트 `run-local.sh`를 추가해 `dev|prod` 프로필로 로컬 부팅할 수 있게 정리했다.
- Step 4: 기존 `docker/docker.sh`는 호환 래퍼로 전환하고 README 실행 가이드를 갱신했다.
- 검증: `bash -n`으로 스크립트 문법을 점검했다.
