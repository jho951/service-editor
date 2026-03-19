# 2026-03-20 문서 복구 API 구현

- 작업 목적: 현재 브랜치에서 진행한 문서 복구 API 구현 내용을 `prompts/` 로그 정책에 맞춰 한 파일로 정리한다.
- 변경 범위: 문서 복구 API 스켈레톤, 서비스 복구 로직, 문서 소속 블록 복구 연동, WebMvc/서비스/통합 테스트, HTTP 메서드 정책 반영
- 관련 요구사항: `docs/REQUIREMENTS.md`의 문서 soft delete / restore 정책

## Step 1. 문서 복구 API 스켈레톤 추가

- `DocumentController`에 문서 복구 엔드포인트를 추가했다.
- `DocumentService` 인터페이스와 `DocumentServiceImpl`에 `restore(UUID documentId, String actorId)` 진입점을 연결했다.
- 복구 요청은 사용자 식별자 헤더를 받아 서버에서 처리하는 형태로 맞췄다.

## Step 2. 문서 복구 서비스 로직 구현

- 삭제된 문서만 복구 대상으로 조회하도록 복구 진입 로직을 구현했다.
- 루트 삭제 문서는 부모 검증 없이 복구 가능하도록 처리했다.
- 삭제된 자식 문서는 부모 문서 상태를 검증한 뒤 복구하도록 했다.
- 부모 문서도 삭제 상태이면 자식 문서 단독 복구를 막고 `INVALID_REQUEST`로 실패 처리하도록 했다.
- 복구 시점은 `deletedAt = null`로 되돌리고, `updatedBy`, `updatedAt`을 함께 갱신하도록 repository bulk update를 추가했다.

## Step 3. 하위 문서 및 소속 블록 복구 연동

- 복구 대상 문서의 삭제된 하위 문서를 재귀적으로 수집해 함께 복구하도록 확장했다.
- `DocumentRepository.restoreDeletedByIds(...)`를 통해 복구 대상 문서들을 한 번에 복구하도록 정리했다.
- 각 복구 대상 문서마다 `BlockService.restoreAllByDocumentId(...)`를 호출해 소속 삭제 블록도 함께 복구하도록 연결했다.
- 다른 문서에 속한 블록은 복구 대상에 포함되지 않도록 범위를 제한했다.

## Step 4. 테스트 보강

- WebMvc 테스트에 복구 성공, 문서 없음, 이미 활성 문서, 사용자 헤더 없음 케이스를 추가했다.
- 서비스 단위 테스트에 루트 문서 복구, 활성 부모 밑 자식 문서 복구, 삭제된 부모 밑 자식 복구 실패, 하위 문서 및 블록 복구 위임 케이스를 추가했다.
- Boot 통합 테스트에 삭제 문서와 해당 문서 소속 삭제 블록 복구, 다른 문서 블록 보존, 문서 없음, 이미 활성 문서 실패 케이스를 추가했다.

## Step 5. HTTP 메서드 정책 반영

- 초기 구현/테스트 단계 이후 프로젝트 정책에 맞춰 문서 복구 API HTTP 메서드를 `PATCH`에서 `POST /v1/documents/{documentId}/restore`로 정리했다.
- 복구 API 의미를 "상태 전이 실행"으로 보고 별도 action endpoint 스타일을 따르도록 맞춘 변경이다.
