# document-service

문서와 블록 데이터를 관리하는 Spring Boot 기반 백엔드 서비스입니다. 현재 저장소는 Gradle 멀티모듈 구조로 정리되어 있으며, 문서(Document)와 블록(Block) CRUD를 확장해 나가는 기준 프로젝트입니다.

## Contract Source
- Contract Repo: https://github.com/jho951/contract
- Contract Sync: `CONTRACT_SYNC.md`
- Repo Role: frontend consumer
- Branch: main

## Contract Scope
- Node-based document, page, and block flows
- Tree operations: list, detail, edit, save, move, restore, trash
- Editor Node API and block-service flows exposed through the gateway

## 프로젝트 개요

- 문서 메타데이터와 문서 내부 블록 구조를 저장, 조회, 수정, 삭제하는 서비스를 목표로 합니다.
- 현재는 멀티모듈 백엔드 구조와 실행 환경이 정리된 상태이며, 문서/블록 CRUD는 단계적으로 구현할 예정입니다.
- 데이터 저장은 MySQL, 애플리케이션 실행은 Spring Boot, 영속 계층은 Spring Data JPA를 사용합니다.
- 요청 감사 이벤트는 `audit-log` 공통 모듈로 발행합니다.

## 현재 사용되는 아키텍처

이 저장소는 Gradle 멀티모듈 구조를 사용합니다.

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

로컬 실행은 `scripts/run-local.sh`를 사용합니다.

```bash
# 기본(dev)
bash scripts/run-local.sh

# prod 프로필로 실행
bash scripts/run-local.sh prod
```

### Docker 실행 스크립트

도커 실행은 `scripts/run-docker.sh`를 사용합니다.

기본 실행(인자 없음): `dev up`

```bash
# dev 환경 전체 빌드/기동/로그
bash scripts/run-docker.sh dev all

# prod 환경 기동
bash scripts/run-docker.sh prod up

# 인자 없이 실행하면 dev up
bash scripts/run-docker.sh
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

- dev: `docker/docker-compose.dev.yml`
- prod: `docker/docker-compose.prod.yml`

참고: 기존 `docker/docker.sh`는 하위 호환용 래퍼로 유지되며 내부적으로 `scripts/run-docker.sh`를 호출합니다.

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
- 문서(Document)와 블록(Block) CRUD 확장 예정 구조

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
