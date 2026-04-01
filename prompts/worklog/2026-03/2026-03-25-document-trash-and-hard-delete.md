# 2026-03-25 문서 hard delete / 휴지통 / 자동 영구 삭제 구현

- 작업 목적: 문서 삭제 정책을 `hard delete + 별도 휴지통 엔드포인트` 구조로 재정의하고, 휴지통 조회/복구/자동 영구 삭제까지 구현한다.
- 관련 요구사항: `docs/REQUIREMENTS.md`
- 관련 검토 문서: `docs/discussions/2026-03-25-document-hard-delete-and-trash-endpoint-review.md`
- 관련 로드맵: `docs/roadmap/v2/documents/document-trash.md`

## Step 1. 삭제 정책 문서 정리

- 문서 기본 삭제를 hard delete로 전환하고 휴지통 이동을 `PATCH /v1/documents/{documentId}/trash`로 분리하는 방향으로 discussion 문서를 다시 정리했다.
- `docs/REQUIREMENTS.md`에 hard delete, 휴지통 이동, 현재 테스트 기준 5분 복구/자동 영구 삭제 정책을 반영했다.
- `docs/roadmap/v2/documents/document-trash.md`를 문서 도메인 기준으로 정리했다.

## Step 2. 기본 문서 삭제 hard delete 전환

- `DELETE /v1/documents/{documentId}`가 대상 문서, 하위 문서, 각 문서 소속 블록을 물리 삭제하도록 서비스 로직을 변경했다.
- 엔티티/DB cascade 전제를 유지한 채 삭제 경로를 hard delete로 전환했다.
- 서비스 테스트와 API 통합 테스트를 hard delete 기준으로 갱신했다.

## Step 3. 휴지통 이동 API 추가

- `PATCH /v1/documents/{documentId}/trash`를 추가했다.
- 휴지통 이동 시 `deletedAt`을 기록하고, 하위 문서와 각 문서 소속 블록도 함께 soft delete 하도록 구현했다.
- WebMvc / 서비스 / 통합 테스트에 성공, 문서 없음, 이미 휴지통 상태 실패 케이스를 추가했다.

## Step 4. 휴지통 복구 API 보강

- 복구 엔드포인트는 `POST /v1/documents/{documentId}/restore`를 유지했다.
- 현재 테스트 기준 `deletedAt + 5분` 이전 문서만 복구 가능하도록 만료 검증을 추가했다.
- 부모 문서가 삭제 상태인 경우 자식 문서 단독 복구를 계속 실패 처리하도록 유지했다.
- 하위 문서와 각 문서 소속 블록까지 함께 복구하도록 기존 흐름을 유지했다.

## Step 5. 자동 영구 삭제 스케줄러 추가

- 실행 주기 등록은 `documents-boot`에, 실제 purge 로직은 `documents-infrastructure` 서비스에 배치했다.
- `@EnableScheduling`과 1분 fixed delay 스케줄러를 추가했다.
- 만료 기준은 현재 테스트 기준 `deletedAt + 5분`이며, 상수는 `DocumentTrashPolicy.RETENTION_MINUTES`로 분리했다.
- purge 대상은 만료된 휴지통 루트 문서만 조회하고, hard delete cascade로 하위 문서와 블록까지 함께 정리하도록 구현했다.

## Step 6. 휴지통 목록 조회 API 추가

- `GET /v1/workspaces/{workspaceId}/trash/documents`를 추가했다.
- 조회 대상은 `deletedAt is not null` 문서만 포함하고, `deletedAt` 내림차순 정렬로 반환한다.
- 응답에는 `documentId`, `title`, `parentId`, `deletedAt`, `purgeAt`를 포함하도록 전용 DTO를 추가했다.
- `purgeAt`는 `deletedAt + 5분`으로 계산하며, 활성 문서는 목록에 포함되지 않도록 구현했다.

## Step 7. 디버그 런북 보강

- `docs/runbook/DEBUG.md`에 hard delete, 휴지통 이동, 복구, 휴지통 조회, 자동 영구 삭제 재현 절차를 추가했다.
- 휴지통 만료 복구 실패와 자동 영구 삭제 미동작 시 점검 항목을 보강했다.

## 테스트 실행 결과

- `./gradlew :documents-infrastructure:test --tests com.documents.service.DocumentServiceImplTest` 통과
- `./gradlew :documents-api:test --tests com.documents.api.document.DocumentControllerWebMvcTest` 통과
- `./gradlew :documents-boot:test --tests com.documents.api.document.DocumentApiIntegrationTest` 통과

## 변경 경로

- 정책 문서: `docs/discussions/2026-03-25-document-hard-delete-and-trash-endpoint-review.md`
- 요구사항: `docs/REQUIREMENTS.md`
- 로드맵: `docs/roadmap/v2/documents/document-trash.md`
- 디버그 런북: `docs/runbook/DEBUG.md`
