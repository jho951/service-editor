# 2026-03-25 Document Version Concurrency

- 날짜: 2026-03-25
- 작업 목적: `Document.version`을 문서 전체 상태 기준으로 확장하고 문서 메타 수정, 블록 변경, 공개 상태 변경에 대한 동시성 정책을 일관되게 맞춘다.

## Step 1. 문서 메타 수정 version 검증

- `PATCH /v1/documents/{documentId}` 요청에 `version` 필드를 추가했다.
- request version과 현재 `Document.version`이 다르면 `409 Conflict`를 반환하도록 서비스 로직을 보강했다.
- 제목, 아이콘, 커버, 부모 변경이 실제로 반영될 때만 version 증가 대상이 되도록 정리했다.
- 동일 상태 요청은 no-op으로 처리하고 version을 유지하도록 맞췄다.

## Step 2. 블록 변경 시 document version 연동

- block create, update, move, delete 성공 시 같은 문서의 `Document.version`이 증가하도록 연결했다.
- block update, move no-op이면 `Document.version`을 증가시키지 않도록 맞췄다.
- transaction 저장 경로에서는 batch 끝에서 `Document.version`이 한 번만 증가하도록 기존 정책을 유지했다.

## Step 3. 문서 공개 상태와 version 정책 확장

- `Document.visibility` 필드를 `PUBLIC`, `PRIVATE` enum으로 추가하고 기본값을 `PRIVATE`로 정했다.
- `PATCH /v1/documents/{documentId}/visibility` API를 추가했다.
- 공개 상태가 실제로 바뀌면 `Document.version` 증가 대상이 되도록 연결했다.
- 같은 공개 상태를 다시 요청하면 no-op으로 처리하고 version을 유지하도록 맞췄다.

## Step 4. 문서와 discussion 반영

- `docs/REQUIREMENTS.md`에 문서 전체 version 정책, no-op 규칙, 프론트 기준 version 규칙을 반영했다.
- `docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md`에 `Document.version`과 `Block.version` 역할 분리, 공개 상태 변경 포함 이유, 프론트 기준값 시나리오를 보강했다.

## Step 5. 회귀 테스트 및 통합 테스트 보정

- 문서 수정 통합 테스트 요청 payload에 `version`을 추가했다.
- 공개 상태 변경 통합 테스트와 visibility 스키마 테스트를 추가했다.
- 문서 삭제/복구 경로에서는 block bulk 처리 중 document version 증가를 억제해 삭제된 문서에 대한 불필요한 `409 Conflict`를 막았다.

## Step 6. 문서 수정 API parentId 제거

- `PATCH /v1/documents/{documentId}`에서 `parentId` 입력을 제거했다.
- 문서 메타 수정 API는 제목, 아이콘, 커버, 공개 상태만 담당하고 부모 변경은 `POST /v1/documents/{documentId}/move`로만 처리하도록 정리했다.
- 관련 서비스 시그니처와 단위 테스트, WebMvc 테스트, 통합 테스트를 현재 계약에 맞게 수정했다.

## 테스트 결과

- 실행 명령:
  - `./gradlew :documents-infrastructure:test --tests com.documents.service.DocumentServiceImplTest --tests com.documents.service.BlockServiceImplTest --tests com.documents.service.DocumentTransactionServiceImplTest :documents-api:test --tests com.documents.api.document.DocumentControllerWebMvcTest --tests com.documents.api.block.AdminBlockControllerWebMvcTest :documents-boot:test --tests com.documents.schema.PersistenceSchemaIntegrationTest --tests com.documents.api.document.DocumentApiIntegrationTest --tests com.documents.api.document.DocumentTransactionApiIntegrationTest --tests com.documents.api.document.DocumentTransactionConcurrencyIntegrationTest`
- 결과: 전체 통과

## 문서 반영 내용

- REQUIREMENTS 반영 완료
- discussion 반영 완료
- ADR 미작성
  - 기존 optimistic locking 범위 안에서 문서 version 의미를 구체화한 변경이며, 되돌리기 어려운 아키텍처 전환보다는 현재 저장 정책의 세부 규칙 정리에 가깝기 때문
