# 2026-03-27 Docker Env Split And Run Scripts

- 목적: Docker 실행 환경을 dev/prod로 분리하고 실행 스크립트를 local/docker 2종으로 정리한다.
- Step 1: `docker/docker-compose.dev.yml`, `docker/docker-compose.prod.yml`를 추가해 환경별 compose를 분리했다.
- Step 2: 루트 `run-docker.sh`를 추가해 `dev|prod` + 액션(`all|build|up|down|logs|restart|nuke|ps`) 실행 구조를 만들었다.
- Step 3: 루트 `run-local.sh`를 추가해 `dev|prod` 프로필로 로컬 부팅할 수 있게 정리했다.
- Step 4: 기존 `docker/docker.sh`는 호환 래퍼로 전환하고 README 실행 가이드를 갱신했다.
- 검증: `bash -n`으로 스크립트 문법을 점검했다.
- Step 5: 스크립트를 `scripts/` 디렉토리로 이동하고, `run-docker.sh`는 인자 없이 실행 시 `dev up` 기본 동작으로 보정했다. `.env` 파일은 선택 로딩으로 변경했다.
- Step 6: compose 파일에서 호스트 포트 노출을 제거하고, `documents-private` + `service-backbone-shared` 이중 네트워크로 전환했다. 앱은 `documents-service` 별칭으로 서비스 백본 네트워크에 붙는다.
- Step 7: `CONTRACT_SYNC.md`를 추가하고 contract repo main SHA(`79dcbadd3428749cd2f4d0615f8443bdfe8aae5a`)로 env 계약 동기화를 기록했다.
- Step 8: YAML 파싱 오류를 일으키던 compose 들여쓰기를 복구하고, `run-docker.sh`의 공유 네트워크 변수명을 `SERVICE_SHARED_NETWORK`로 맞췄다.
- Step 9: `Document`와 `Block`의 UUID PK를 `@JdbcTypeCode(SqlTypes.CHAR)`로 고정하고, 스키마 테스트에서 `document_id`/`block_id`가 문자형 UUID로 생성되는지 검증하도록 보강했다.
