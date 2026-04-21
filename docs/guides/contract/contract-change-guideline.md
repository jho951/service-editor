# Contract Change Workflow

이 문서는 `editor-service` 구현 코드를 수정한 뒤 계약 변경을 어떻게 처리할지 정리한다. 기준 계약의 원본은 `https://github.com/jho951/service-contract`이고, 이 repo는 `contract.lock.yml`로 특정 contract commit을 따른다.

## 먼저 판단한다

서비스 코드 변경이 외부에서 관측되는 동작을 바꾸면 계약 변경이다. 아래 항목 중 하나라도 해당하면 `service-contract`를 함께 수정한다.

- endpoint 추가, 삭제, 경로 변경
- request body, query, path variable, header 변경
- response field, envelope, enum, timestamp field 이름 변경
- HTTP status code 또는 error response 변경
- 인증, 인가, internal caller proof 방식 변경
- env, port, health, ready, Docker Compose, CI/CD 변경
- Redis key, TTL, monitoring target처럼 다른 repo가 의존하는 운영 계약 변경

위 항목에 해당하지 않는 순수 내부 구현, 리팩터링, private method, repository query 최적화, 테스트 보강은 이 repo만 수정한다.

## 내부 구현만 바뀐 경우

`service-contract`, `docs/openapi`, `contract.lock.yml`을 건드리지 않는다.

```bash
git status --short
git diff
./gradlew clean test
git add <changed-files>
git commit -m "Update editor-service implementation"
git push origin <branch>
```

## 계약도 바뀐 경우

### 1. service-contract를 먼저 수정한다

```bash
cd /Users/jhons/Downloads/BE/contract/service-contract
git status --short
```

API shape이 바뀌면 OpenAPI artifact를 수정한다. 설명 문서도 함께 맞춘다.

- `repositories/editor-service/api.md`
- `repositories/editor-service/security.md`
- `repositories/editor-service/ops.md`

공통 규칙이 바뀌면 `shared/*.md`를 수정한다. 예를 들어 header, error envelope, env, CI/CD, security 규칙은 service별 문서에만 숨기지 않는다.

### 2. service-contract를 커밋하고 push한다

```bash
git add <contract-files>
git commit -m "Update editor-service contract"
git push origin main
git rev-parse HEAD
```

`git rev-parse HEAD`로 나온 SHA가 이 repo의 `contract.lock.yml`에 들어갈 commit이다.

### 3. 이 repo의 local copy와 lock을 갱신한다

이 repo는 현재 service local OpenAPI copy를 기준 파일로 두지 않는다. HTTP API shape이 새로 생기면 `service-contract/artifacts/openapi`에 artifact를 만들고, 이후 이 repo에도 local copy를 둘지 결정한다.

`contract.lock.yml`의 commit을 새 contract commit으로 바꾼다.

```yaml
contract:
  repo: https://github.com/jho951/service-contract
  ref: main
  commit: <new-service-contract-commit>
```

`service.consumes`에는 이 repo가 실제로 소비하는 contract 문서와 artifact 경로가 들어 있어야 한다. 새 OpenAPI나 shared 문서를 추가했다면 `consumes`에도 추가한다.

### 4. 이 repo에서 검증한다

```bash
git status --short
git diff
./gradlew clean test
```

Docker Compose, health, ready, CI/CD가 바뀐 경우에는 `.github/workflows/ci.yml`의 `COMPOSE_CONFIG_COMMAND`와 같은 검증을 로컬에서도 실행한다.

### 5. 이 repo를 커밋하고 push한다

```bash
git add <implementation-files> contract.lock.yml
# OpenAPI local copy가 있는 repo면 docs/openapi/*.yml도 함께 add
git commit -m "Align editor-service implementation with contract"
git push origin <branch>
```

## 어디에 올리는가

| 변경 내용 | 올릴 위치 |
| --- | --- |
| API path, request, response, status, auth 변경 | `service-contract/artifacts/openapi`, `service-contract/repositories/editor-service` |
| 공통 header, error, security, env, CI/CD 규칙 변경 | `service-contract/shared` |
| 운영 port, health, ready, compose, monitoring target 변경 | `service-contract/repositories/editor-service/ops.md` 또는 관련 운영 문서 |
| 구현 코드, 테스트, local docs | 이 repo |
| 이 repo가 따르는 contract commit | 이 repo의 `contract.lock.yml` |

## CI가 확인하는 것

- `contract-check.yml`은 `contract.lock.yml`의 repo, service name, ref, commit, consumes를 확인한다.
- `ci.yml`은 `contract-lock -> setup-runtime -> test -> build -> image` 순서로 돈다.
- `cd.yml`은 `deploy-gate -> image -> deploy -> health-check -> smoke-test` 순서로 돈다.
- 계약 영향 파일을 바꾸면서 `contract.lock.yml`을 갱신하지 않으면 PR에서 실패하는 것이 정상이다.
