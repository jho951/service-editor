# 디버그 런북

## 로컬 재현 절차

1. `./gradlew :documents-boot:bootRun`으로 실행한다. 로컬 기본 설정은 루트 `gradle.properties`에서 주입된다.
2. `http://localhost:8080/swagger-ui` 로 API 문서를 확인한다.
3. DB 연결 문제가 있으면 `documents-boot/src/main/resources/application-dev.yml`과 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 점검한다. 기본 로컬 DB 이름은 `documentsdb`이고 기본 계정은 `documents/documents`다.
4. `dev` 프로파일은 `ddl-auto=update`라서 누락된 테이블은 자동 생성된다. `prod` 프로파일은 `ddl-auto=none`이라 스키마를 자동 변경하지 않는다.
5. MySQL JDBC URL은 서버/스키마가 `utf8mb4`인 상태를 전제로 `connectionCollation=utf8mb4_unicode_ci`만 사용한다. `characterEncoding=utf8mb4`를 직접 넣으면 드라이버에서 부팅 실패가 날 수 있다.
6. Workspace API 확인이 필요하면 `POST /v1/workspaces`로 생성 후 `GET /v1/workspaces/{workspaceId}`로 단건 조회를 재현한다.
7. Document 조회 API 확인이 필요하면 `POST /v1/workspaces/{workspaceId}/documents`로 문서를 만든 뒤 `GET /v1/workspaces/{workspaceId}/documents`와 `GET /v1/documents/{documentId}`를 순서대로 호출한다.
8. 문서 생성/정렬 이슈를 볼 때는 응답의 `sortKey`가 비어 있지 않은지, 같은 `parentId` 아래에서 증가하는지 확인한다.
9. API 통합 테스트는 저장소 루트에서 `./gradlew :documents-boot:test`로 실행한다. 하위 모듈 검증이 필요하면 같은 방식으로 `:documents-api:test`, `:documents-core:test`, `:documents-infrastructure:test`를 선택 실행한다.
10. 빠른 API 계약 확인은 `./gradlew :documents-api:test`, 영속/서비스 구현 확인은 `./gradlew :documents-infrastructure:test`를 우선 사용하고, 최종 조립 확인 시 `:documents-boot:test`를 실행한다.
11. 스키마 명명 규칙 확인이 필요하면 H2 `INFORMATION_SCHEMA.COLUMNS` 또는 MySQL `information_schema.columns`에서 `workspaces.workspace_id`, `documents.document_id` 컬럼이 생성됐는지 확인한다.

## 확인할 로그

- 애플리케이션 부팅 로그에서 `com.documents` 패키지 스캔 여부를 확인한다.
- Hibernate/JPA 초기화 로그와 datasource 초기화 로그를 확인한다.

## 자주 발생하는 장애

- `NoSuchBeanDefinitionException`: 모듈 의존성 누락 또는 패키지 스캔 범위 문제
- `Not a managed type`: 엔티티 패키지 스캔 누락 또는 `@Entity`/`@MappedSuperclass` 선언 문제
- `Communications link failure`: MySQL 기동 전 애플리케이션 실행 또는 DB 환경변수 오설정
- `Access denied for user ...`: 로컬 앱 계정과 MySQL 컨테이너 계정이 불일치한 상태. 기본 로컬 계정은 `documents/documents`로 맞춘다.
- `Unsupported character encoding 'utf8mb4'`: JDBC URL의 `characterEncoding` 값을 `utf8mb4`로 설정한 상태. `UTF-8`로 수정하고 필요하면 `connectionCollation=utf8mb4_unicode_ci`를 함께 사용한다.
- `Table "DOCUMENTS" not found` 또는 유사 스키마 오류: 마이그레이션 전 스키마/설정이 남아 있거나 로컬 DB가 최신 설정(`documentsdb`)과 맞지 않는 상태
- `Table "WORKSPACES" not found`: Workspace 엔티티가 생성되지 않았거나 테스트/로컬 프로파일의 DDL 설정이 비활성화된 상태
- `404` Workspace 조회 실패: 생성된 `workspaceId`가 아니거나 테스트 데이터 초기화 후 다시 조회한 경우
- `400` Document 목록 조회 실패: 커스텀 JPA 쿼리의 named parameter 바인딩 누락 또는 soft delete 조건/정렬 쿼리 오류 여부를 확인한다.
- 스키마 검증 실패: 엔티티 `@Column(name = ...)` 값과 실제 생성된 DDL 컬럼명이 일치하는지 확인한다. PK는 `id`가 아니라 `${domain}_id` 규칙을 따른다.

## 복구 절차

- `./gradlew clean :documents-boot:test`로 멀티모듈 의존성과 컨텍스트 기동을 검증한다.
- Docker 사용 시 `docker compose -f docker/docker-compose.yml up --build`로 재빌드한다.
