# 2026-03-20 문서 move API 구현

- 작업 목적: 현재 브랜치에서 진행한 문서 move API 구현 내용을 `prompts/` 로그 정책에 맞춰 한 파일로 정리한다.
- 변경 범위: 요구사항 문서 반영, move API 스켈레톤, 서비스 검증 로직, 위치 해석 및 sortKey 계산, 트랜잭션 반영, WebMvc/서비스/통합 테스트
- 관련 요구사항: `docs/REQUIREMENTS.md`의 문서 이동 및 reorder 정책

## Step 1. 요구사항과 API 책임 분리

- 기존 문서 수정 API에서 부모 변경과 형제 순서 변경 책임을 분리하고, 별도 `POST /v1/documents/{documentId}/move` API로 정리했다.
- move API는 문서 메타데이터 수정이 아니라 구조 변경과 순서 변경만 담당하도록 범위를 고정했다.
- 위치 해석 입력은 `targetParentId`, `afterDocumentId`, `beforeDocumentId` 조합으로 정의했다.

## Step 2. 요구사항 문서 반영

- `docs/REQUIREMENTS.md`에 문서 move API 명세를 추가했다.
- 이동 대상 활성 문서 제한, 부모 활성/워크스페이스 검증, 자기 자신 부모 지정 금지, 순환 이동 금지 규칙을 반영했다.
- `afterDocumentId`, `beforeDocumentId`의 부모 일치, 인접성, 모순 검증 규칙과 no-op 허용 정책을 함께 정리했다.

## Step 3. API 스켈레톤 추가

- `DocumentController`에 `POST /v1/documents/{documentId}/move` 엔드포인트를 추가했다.
- `MoveDocumentRequest` DTO에 `targetParentId`, `afterDocumentId`, `beforeDocumentId`를 정의했다.
- `DocumentService`와 `DocumentServiceImpl`에 `move(...)` 진입점을 연결했다.

## Step 4. 서비스 검증 로직 구현

- 이동 대상 문서는 활성 문서만 허용하도록 `findActiveDocument(...)` 검증을 재사용했다.
- `findValidParentForMove(...)`에서 대상 부모 활성 여부, 같은 워크스페이스 여부, 자기 자신 부모 지정, 순환 이동 여부를 검증하도록 했다.
- `targetParentId == null`이면 루트 이동을 허용하도록 처리했다.

## Step 5. 위치 해석과 sortKey 계산 구현

- 대상 부모 아래 활성 형제 문서를 조회하는 repository 메서드를 추가했다.
- `DocumentSortKeyGenerator`에 move 전용 위치 기반 `sortKey` 계산을 추가했다.
- `afterDocumentId`만 있는 경우 뒤 위치, `beforeDocumentId`만 있는 경우 앞 위치, 둘 다 없는 경우 마지막 위치, 둘 다 있는 경우 두 문서 사이 위치로 해석했다.
- `afterDocumentId`, `beforeDocumentId`가 같은 부모 집합의 활성 형제이며 서로 인접한 경우만 허용하도록 했다.
- 정렬 키 공간 부족은 기존 프로젝트 정책대로 `SORT_KEY_REBALANCE_REQUIRED` 예외로 변환했다.

## Step 6. 트랜잭션 반영과 no-op 정책

- move 성공 시 `parentId`, `sortKey`, `updatedBy`를 한 트랜잭션 안에서 함께 갱신하도록 구현했다.
- `updatedAt`은 엔티티 update 시점에 함께 갱신되도록 현재 JPA 정책을 따랐다.
- 동일 위치 no-op 요청은 성공으로 허용하되 실제 필드 갱신은 생략하도록 정리했다.

## Step 7. 테스트 보강

- WebMvc 테스트에 move 성공, 문서 없음, 삭제 문서, 자기 자신 부모 지정, 사용자 헤더 없음 케이스를 추가했다.
- 서비스 단위 테스트에 루트 이동, 같은 부모 내 reorder, 다른 부모 이동, 자기 자신 부모 지정 실패, 순환 이동 실패, 다른 워크스페이스 부모 실패, 모순된 anchor 실패, 정렬 키 공간 부족 실패를 추가했다.
- Boot 통합 테스트에 audit 필드 반영, 같은 부모 내 reorder, 다른 부모 이동, 문서 없음, 삭제 문서, 자기 자신 부모 지정, 순환 이동 실패를 추가했다.
- 통합 테스트 작성 중 전역 `sortKey` 정렬 정책과 맞지 않는 기대 순서를 현재 구현 기준으로 조정했다.

## Step 8. 핵심 정책 정리

- 이동 대상은 활성 문서로 제한하고, 존재하지 않거나 삭제된 문서는 `DOCUMENT_NOT_FOUND`로 처리한다.
- 부모 문서는 활성 상태이면서 같은 워크스페이스에 속해야 한다.
- 하위 문서를 부모로 지정하는 순환 이동은 `INVALID_REQUEST`로 처리한다.
- 형제 위치 해석은 요청 anchor 문서와 대상 부모의 일관성을 우선 검증한다.
- 정렬 키 간격이 없으면 자동 재정렬 대신 `SORT_KEY_REBALANCE_REQUIRED`를 반환한다.

## Step 9. 테스트 실행 결과

- 실행 명령:
  - `./gradlew --no-daemon :documents-api:test --tests com.documents.api.document.DocumentControllerWebMvcTest :documents-infrastructure:test --tests com.documents.service.DocumentServiceImplTest :documents-boot:test --tests com.documents.api.document.DocumentApiIntegrationTest`
- 결과:
  - `BUILD SUCCESSFUL`
  - move 관련 WebMvc, 서비스 단위, 통합 테스트 모두 통과

## Step 10. REQUIREMENTS 반영 여부

- 반영함
- `docs/REQUIREMENTS.md`에 문서 move API 엔드포인트, request body, 검증 규칙, 위치 해석 규칙, no-op 정책을 추가했다.
