# 디버그 런북

## 로컬 재현 절차

1. `./gradlew :documents-boot:bootRun`으로 실행한다. 로컬 기본 설정은 루트 `gradle.properties`에서 주입된다.
2. `http://localhost:8080/swagger-ui` 로 API 문서를 확인한다.
3. DB 연결 문제가 있으면 실행 프로필에 맞는 설정 파일과 환경변수를 같이 점검한다. `dev`는 `DB_URL_DEV`, `DB_USERNAME_DEV`, `DB_PASSWORD_DEV`, `prod`는 `DB_URL_PROD`, `DB_USERNAME_PROD`, `DB_PASSWORD_PROD`를 우선 사용하고, 기존 공용 키 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`는 fallback으로만 본다.
4. Docker Compose 실행 시에도 `dev`는 `DB_NAME_DEV`, `DB_USERNAME_DEV`, `DB_PASSWORD_DEV`, `MYSQL_ROOT_PASSWORD_DEV`, `prod`는 `DB_NAME_PROD`, `DB_USERNAME_PROD`, `DB_PASSWORD_PROD`, `MYSQL_ROOT_PASSWORD_PROD`를 분리해서 맞춘다. 기본 DB 이름은 두 환경 모두 `documentsdb`다.
5. `dev` 프로파일은 `ddl-auto=update`라서 누락된 테이블은 자동 생성된다. `prod` 프로파일은 `ddl-auto=none`이라 스키마를 자동 변경하지 않는다.
6. MySQL JDBC URL은 서버/스키마가 `utf8mb4`인 상태를 전제로 `connectionCollation=utf8mb4_unicode_ci`만 사용한다. `characterEncoding=utf8mb4`를 직접 넣으면 드라이버에서 부팅 실패가 날 수 있다.
7. `/**` 호출 시 `X-User-Id`를 반드시 포함한다. 누락/빈값이면 `401 UNAUTHORIZED`가 반환돼야 한다.
8. 요청 추적이 필요하면 `X-Request-Id`를 함께 보낸다. 미전달 시 서버가 값을 생성해 응답 헤더 `X-Request-Id`로 반환해야 한다.
9. v1 기준 문서 API 확인은 `X-User-Id`를 포함한 상태에서 `POST /documents`로 문서를 만든 뒤 `GET /documents`, `GET /documents/{documentId}`를 순서대로 호출한다.
10. Workspace API는 v1 활성 범위에서 제외되므로, 현재 기본 디버깅 절차에서는 제외한다.
11. 문서 생성/정렬 이슈를 볼 때는 응답의 `sortKey`가 비어 있지 않은지, 같은 `parentId` 아래에서 증가하는지 확인한다.
12. Block 생성 보조 API 확인이 필요하면 `POST /admin/documents/{documentId}/blocks`에 `EditorSaveRequest` 형식의 단건 `BLOCK_CREATE` operation을 보내 `TEXT` 블록을 만든다.
13. Block 정렬 디버깅 시 `sortKey`는 대문자 base36 고정폭 문자열이며, 같은 부모 아래 `ORDER BY sort_key ASC` 결과가 화면 순서와 일치해야 한다.
14. 반복 삽입으로 gap이 없어지면 `SORT_KEY_REBALANCE_REQUIRED(409)`가 반환될 수 있다. 이 경우 즉시 전체 재정렬을 수행하지 않고 후속 reorder/rebalance 작업이 필요하다.
15. Block 생성 실패를 재현할 때는 `parentRef`를 다른 문서의 블록으로 보내거나, 존재하지 않는 `afterRef`를 보내서 `400` 또는 `404` 응답이 요구사항대로 나오는지 확인한다.
16. Block 삭제 보조 API 확인이 필요하면 루트 블록과 하위 블록을 만든 뒤 `DELETE /admin/blocks/{blockId}`에 단건 `BLOCK_DELETE` operation을 보낸다. 대상 블록 트리의 `deletedAt`만 채워지고 같은 문서의 다른 루트 블록은 유지되는지 확인한다.
17. 에디터 표준 저장 경로 확인이 필요하면 `POST /editor-operations/documents/{documentId}/save`에 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE` batch를 보내고 전체 rollback과 `documentVersion` 갱신 여부를 확인한다.
18. API 통합 테스트는 저장소 루트에서 `./gradlew :documents-boot:test`로 실행한다. 하위 모듈 검증이 필요하면 같은 방식으로 `:documents-api:test`, `:documents-core:test`, `:documents-infrastructure:test`를 선택 실행한다.
19. 빠른 API 계약 확인은 `./gradlew :documents-api:test`, 영속/서비스 구현 확인은 `./gradlew :documents-infrastructure:test`를 우선 사용하고, 최종 조립 확인 시 `:documents-boot:test`를 실행한다.
20. 스키마 명명 규칙 확인이 필요하면 H2 `INFORMATION_SCHEMA.COLUMNS` 또는 MySQL `information_schema.columns`에서 `workspaces.workspace_id`, `documents.document_id`, `blocks.block_id` 컬럼이 생성됐는지 확인한다.
21. 문서 계층 삭제 이슈를 확인할 때는 `information_schema.table_constraints`, `information_schema.referential_constraints`에서 `FK_DOCUMENTS_PARENT`가 존재하는지와 `DELETE_RULE = CASCADE`인지 함께 확인한다.
22. 블록 계층 삭제 이슈를 확인할 때는 `FK_BLOCKS_DOCUMENT`, `FK_BLOCKS_PARENT`가 존재하는지와 두 FK 모두 `DELETE_RULE = CASCADE`인지 함께 확인한다.
23. 문서 hard delete 확인이 필요하면 `DELETE /documents/{documentId}`를 호출한 뒤 `documents`, `blocks` 테이블에서 대상 문서, 하위 문서, 각 문서 소속 블록 row가 실제로 사라졌는지 확인한다.
24. 문서 휴지통 이동 확인이 필요하면 `PATCH /documents/{documentId}/trash`를 호출한 뒤 대상 문서, 하위 문서, 각 문서 소속 블록의 `deleted_at`이 같은 흐름으로 채워졌는지와 대상 문서들의 `version`이 각각 `1` 증가했는지 확인한다.
25. 문서 복구 확인이 필요하면 휴지통 이동 직후 `POST /documents/{documentId}/restore`를 호출해 `deleted_at`이 null로 돌아오는지와 대상 문서들의 `version`이 각각 `1` 더 증가했는지 확인한다. `deletedAt + 5분`이 지난 데이터는 복구가 실패해야 한다.
26. 휴지통 목록 확인이 필요하면 `GET /documents/trash`를 호출해 `deletedAt` 내림차순 정렬, `purgeAt = deletedAt + 5분` 계산, 활성 문서 제외 여부를 확인한다.
27. 자동 영구 삭제 확인이 필요하면 `deleted_at`이 현재 시각 기준 5분 이상 지난 문서를 만든 뒤 스케줄러 실행 또는 `DocumentService.purgeExpiredTrash()` 호출로 대상 문서, 하위 문서, 각 문서 소속 블록이 실제 삭제되는지 확인한다.
28. resource binding 상태 확인이 필요하면 `document_resources.status`, `deleted_at`, `purge_at`, `last_error`, `repaired_at`를 조회해 `ACTIVE -> TRASHED -> PENDING_PURGE -> PURGED` 전이와 `BROKEN` 발생 여부를 확인한다.
29. 문서 trash 후 attachment/snapshot 보존을 확인하려면 `PATCH /documents/{documentId}/trash` 뒤 `document_resources.status = TRASHED`인지 확인하고, `POST /documents/{documentId}/restore` 후 다시 `ACTIVE`로 돌아오는지 확인한다.
30. document hard delete 또는 block delete 뒤에는 대상 binding이 `PENDING_PURGE`로 전환되고, `DocumentsResourcePurgeScheduler` 실행 후 `PURGED`로 바뀌는지 확인한다.
31. drift 점검이 필요하면 `DocumentsResourceReconcileScheduler` 실행 후 `document_resources.last_error`, `status = BROKEN` 건수를 확인하고, catalog에만 남은 managed resource가 binding으로 재생성됐는지 확인한다.

## 확인할 로그

- 애플리케이션 부팅 로그에서 `com.documents` 패키지 스캔 여부를 확인한다.
- Hibernate/JPA 초기화 로그와 datasource 초기화 로그를 확인한다.

## 자주 발생하는 장애

- `NoSuchBeanDefinitionException`: 모듈 의존성 누락 또는 패키지 스캔 범위 문제
- `Not a managed type`: 엔티티 패키지 스캔 누락 또는 `@Entity`/`@MappedSuperclass` 선언 문제
- `Communications link failure`: MySQL 기동 전 애플리케이션 실행, 잘못된 프로필의 DB 환경변수 주입, 또는 `DB_URL_DEV`/`DB_URL_PROD` 오설정
- `Access denied for user ...`: 앱 계정과 MySQL 컨테이너 계정이 불일치한 상태. 같은 프로필에서 `DB_USERNAME_*`, `DB_PASSWORD_*`와 MySQL 계정을 같이 맞춘다.
- `Unsupported character encoding 'utf8mb4'`: JDBC URL의 `characterEncoding` 값을 `utf8mb4`로 설정한 상태. `UTF-8`로 수정하고 필요하면 `connectionCollation=utf8mb4_unicode_ci`를 함께 사용한다.
- `Table "DOCUMENTS" not found` 또는 유사 스키마 오류: 마이그레이션 전 스키마/설정이 남아 있거나 로컬 DB가 최신 설정(`documentsdb`)과 맞지 않는 상태
- `Table "WORKSPACES" not found`: 현재 v1 문서 흐름에는 직접 영향이 없지만, Workspace 백업 코드를 다시 활성화했거나 관련 테스트를 돌릴 때 스키마가 누락된 상태
- `400` Document 목록 조회 실패: 커스텀 JPA 쿼리의 named parameter 바인딩 누락 또는 soft delete 조건/정렬 쿼리 오류 여부를 확인한다.
- `404` Block 생성 실패: `parentRef`가 soft delete되었거나 다른 문서의 블록을 부모로 지정한 경우인지 확인한다.
- `400` Block 생성 실패: `afterRef`/`beforeRef`가 같은 sibling gap을 가리키는지, 요청 `parentRef`와 같은 형제 집합인지 확인한다.
- `404` Block 삭제 실패: 대상 `blockId`가 이미 soft delete되었거나 존재하지 않는지 확인한다.
- `409` sort key 충돌: 같은 gap에 삽입이 누적되어 재균형이 필요한 상태인지 확인한다.
- 휴지통 복구 실패: `deleted_at`이 현재 시각 기준 5분 이상 지났는지, 부모 문서가 여전히 삭제 상태인지 확인한다.
- 휴지통 목록 응답 이상: `deleted_at` 내림차순 정렬 쿼리와 `purgeAt` 계산 로직이 같은 보관 시간 상수를 쓰는지 확인한다.
- 자동 영구 삭제 미동작: `@EnableScheduling` 적용 여부, 스케줄러 등록 여부, 만료 기준 `deletedAt <= now - 5분` 조건, purge 루트 조회 쿼리를 함께 확인한다.
- 스키마 검증 실패: 엔티티 `@Column(name = ...)` 값과 실제 생성된 DDL 컬럼명이 일치하는지 확인한다. PK는 `id`가 아니라 `${domain}_id` 규칙을 따른다.

## 복구 절차

- `./gradlew clean :documents-boot:test`로 멀티모듈 의존성과 컨텍스트 기동을 검증한다.
- Docker 사용 시 `docker compose -f docker/docker-compose.yml up --build`로 재빌드한다.
