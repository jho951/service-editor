# 2026-03-21 editor transaction implementation

## Step 1. create + replace_content transaction 최소 경로 구현

- `POST /v1/documents/{documentId}/transactions` 엔드포인트와 request/response DTO를 추가했다.
- v1 첫 구현 범위는 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT` 두 operation만 처리하도록 두고, `tempId -> real blockId` 매핑과 applied operation 응답을 반환하도록 연결했다.
- `documents-core`에 transaction command/result 모델과 서비스 인터페이스를 추가하고, `documents-infrastructure`에 transaction orchestration 구현을 추가했다.
- 같은 batch 안의 `tempId` 대상 `replace_content`는 transaction 컨텍스트에서 실제 `blockId`와 version으로 해석하도록 구현했다.
- `BlockContentValidator`는 null 허용으로 조정하고, 필수 여부는 각 요청 DTO가 맡도록 바꿔 nested transaction DTO 검증과 기존 block API 검증을 함께 맞췄다.
- 검증은 `:documents-api:test --tests 'com.documents.api.document.DocumentControllerWebMvcTest' -x :documents-boot:test` 와 `:documents-infrastructure:test --tests 'com.documents.service.DocumentTransactionServiceImplTest' -x :documents-boot:test`로 확인했다.
- 의도가 숨는 조건문은 인라인으로 두지 않고, `validateTempIdIsUnique`, `registerTempBlockContext`, `isTempBlockReference` 같은 이름 있는 메서드로 정리하는 기준을 현재 구현에 반영했다.
