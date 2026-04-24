# editor-service

문서와 블록 데이터를 관리하는 Spring Boot 기반 백엔드 서비스입니다. 현재 저장소는 Gradle 멀티모듈 구조로 정리되어 있으며, 문서(Document)와 블록(Block) CRUD를 확장해 나가는 기준 프로젝트입니다.

## Contract Source

- 공통 계약 레포: `https://github.com/jho951/service-contract`
- 계약 동기화 기준 파일: [contract.lock.yml](contract.lock.yml)
- 계약 변경 절차: [contract-change-guideline.md](docs/guides/contract/contract-change-guideline.md)
- 이 서비스의 코드 SoT: `editor-service` `main`
- PR에서는 `.github/workflows/contract-check.yml`이 lock 파일과 계약 영향 변경 여부를 검사합니다.
- 인터페이스 변경 시 본 저장소 구현보다 계약 레포 변경을 먼저 반영합니다.

## 프로젝트 개요

- 문서 메타데이터와 문서 내부 블록 구조를 저장, 조회, 수정, 삭제하는 서비스를 제공합니다.
- 현재는 문서 CRUD, 휴지통/복구, 블록 조회, 관리자 블록 보조 API, 에디터 save/move API가 구현된 상태입니다.
- TEXT block 본문은 `rich_text` JSON을 사용하며, 현재 validator는 optional `content.blockType`으로 `paragraph`, `heading1`, `heading2`, `heading3`를 허용합니다.
- `segment`는 여전히 `text`, `marks`만 허용합니다.
- 데이터 저장은 MySQL, 애플리케이션 실행은 Spring Boot, 영속 계층은 Spring Data JPA를 사용합니다.

## 현재 사용되는 아키텍처

이 저장소는 `editor-service` Gradle 멀티모듈 구조를 사용합니다.

- `documents-boot`: 실행 모듈, 환경 설정, 패키징
- `documents-api`: Controller, 요청/응답 DTO, API 전용 예외/응답 코드, OpenAPI 설정
- `documents-core`: 도메인 모델과 서비스 계약
- `documents-infrastructure`: JPA Repository, 영속 구현

흐름은 다음과 같습니다.

`Client -> documents-api -> documents-core -> documents-infrastructure(JPA) -> MySQL`

## 설치/실행 방법

### 사전 요구사항

- Java 17
- Gradle Wrapper 사용 가능 환경
- Docker
- Docker Compose

### 로컬 Gradle 실행

기본 로컬 실행 설정은 루트의 `gradle.properties`를 사용합니다.

```bash
./gradlew :documents-boot:bootRun
```

프로젝트 구조 확인:

```bash
./gradlew projects
```

테스트 실행:

```bash
./gradlew test
```

### Local 실행 스크립트

로컬 실행은 `scripts/run.local.sh`를 기준으로 사용합니다.

```bash
# 기본(dev)
bash scripts/run.local.sh

# prod 프로필로 실행
bash scripts/run.local.sh prod
```

### Docker 실행 스크립트

도커 실행은 `scripts/run.docker.sh`를 기준으로 사용합니다.

기본 실행(인자 없음): `dev up`

```bash
# dev 환경 전체 빌드/기동/로그
bash scripts/run.docker.sh up dev

# prod 환경 기동
bash scripts/run.docker.sh up prod

# 인자 없이 실행하면 dev up
bash scripts/run.docker.sh up dev
```

지원 동작:

- `all`: 이미지 재빌드 후 컨테이너 기동, 앱 로그 출력
- `build`: 이미지만 빌드
- `up`: 백그라운드로 컨테이너 시작
- `down`: 컨테이너 중지
- `logs`: 로그 팔로우
- `restart`: 재시작
- `nuke`: 컨테이너/볼륨 삭제 + 불필요 이미지 정리
- `ps`: 컨테이너 상태 확인

환경별 compose 파일:

- dev: `docker/dev/compose.yml`
- prod: `docker/prod/compose.yml`
- build only: `docker/compose.build.yml`

참고: 기존 `scripts/run-local.sh`, `scripts/run-docker.sh`, `docker/docker.sh`는 하위 호환용 래퍼로 유지될 수 있고, canonical 스크립트는 `scripts/run.local.sh`, `scripts/run.docker.sh`입니다.

### 배포 메모

- 현재 운영 기본값은 `ECS/Fargate + CodeDeploy blue/green`입니다.
- `docker/prod/compose.yml`은 `EDITOR_SERVICE_IMAGE`를 필수로 받고, 운영 배포에서는 CI/CD가 ECR에 push한 이미지만 pull합니다.
- `GH_TOKEN`, `GITHUB_ACTOR`는 dev 또는 로컬 build에서만 필요합니다.
- dev build 설정은 `docker/compose.build.yml`에만 두고, 실행용 compose에는 포함하지 않습니다.
- EC2 compose 방식은 fallback reference로만 남기고, 무중단 배포가 필요한 표준 운영 배포 방식으로 취급하지 않습니다.

## 기술 스택

- Java 17
- Spring Boot 3.4.x
- Gradle
- Spring Data JPA
- MySQL 8
- Spring Validation
- springdoc-openapi
- Docker / Docker Compose
- Lombok

## 핵심 기능

- Gradle 멀티모듈 기반 문서 서비스 구조
- MySQL 기반 JPA 영속화 준비
- OpenAPI(Swagger) 문서화 설정
- Docker 기반 로컬 실행 환경
- 문서(Document) CRUD와 휴지통/복구
- 문서 단위 블록(Block) 조회
- 관리자 블록 생성/수정/이동/삭제 보조 API
- 에디터 save batch와 문서/블록 move API

## 기여 방법

1. 이슈 또는 작업 목적을 정리합니다.
2. 브랜치를 생성하고 변경 작업을 진행합니다.
3. 요구사항 변경이 있으면 `docs/REQUIREMENTS.md` 갱신 여부를 함께 판단합니다.
4. 중요한 기술 결정은 `docs/decisions/`에 ADR로 기록합니다.
5. 모든 AI 작업은 `prompts/`에 로그를 남깁니다.
6. 변경 후 테스트 또는 최소 실행 검증 결과를 함께 정리합니다.
7. PR에는 프롬프트 로그 경로와 REQUIREMENTS 반영 여부를 명시합니다.

## 라이선스

현재 저장소에 별도 라이선스 파일은 없습니다. 외부 공개 배포 전에는 사용할 라이선스 정책을 확정하고 `LICENSE` 파일을 추가하는 것을 권장합니다.
