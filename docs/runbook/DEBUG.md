# 디버그 런북

## 로컬 재현 절차

1. `./gradlew :documents-boot:bootRun`으로 실행한다. 로컬 기본 설정은 루트 `gradle.properties`에서 주입된다.
2. `http://localhost:8080/swagger-ui`로 API 문서를 확인한다.
3. DB 연결 문제가 있으면 `documents-boot/src/main/resources/application-dev.yml`과 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 점검한다.

## 확인할 로그

- 애플리케이션 부팅 로그에서 `com.documents` 패키지 스캔 여부를 확인한다.
- MyBatis 매퍼 로딩 로그와 datasource 초기화 로그를 확인한다.

## 자주 발생하는 장애

- `NoSuchBeanDefinitionException`: 모듈 의존성 누락 또는 패키지 스캔 범위 문제
- `Invalid bound statement`: `documents-infrastructure`의 mapper XML 경로 또는 namespace 불일치
- `Communications link failure`: MySQL 기동 전 애플리케이션 실행 또는 DB 환경변수 오설정

## 복구 절차

- `./gradlew clean :documents-boot:test`로 멀티모듈 의존성과 컨텍스트 기동을 검증한다.
- Docker 사용 시 `docker compose -f docker/docker-compose.yml up --build`로 재빌드한다.
