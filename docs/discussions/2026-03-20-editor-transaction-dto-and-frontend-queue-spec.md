# 2026-03-20 editor transaction DTO 및 frontend queue 스펙 초안

## 문서 목적

- v1 `POST /v1/documents/{documentId}/transactions`의 request/response 계약을 정리한다.
- 프론트 queue 자료구조와 flush 상태 모델을 같은 문맥에서 맞춘다.
- 프론트 구현자와 서버 구현자가 같은 단어를 같은 의미로 이해하도록 기준선을 만든다.

## 배경

- 에디터 저장 표준 경로는 `transactions` 단일 API로 정리되었다.
- v1 operation은 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE` 4개로 좁혔다.
- block 단위 optimistic lock을 사용하고, 충돌 시 전체 rollback을 사용하기로 했다.
- conflict 응답에는 최신 block `version`, 최신 `content`를 포함하는 방향을 채택했다.

## 검토 범위

- transaction request DTO
- transaction success/conflict response DTO
- frontend queue 자료구조
- queue coalescing 규칙
- flush 상태 전이

## 핵심 질문

1. 프론트는 어떤 필드를 모아서 transaction request를 만들어야 하는가
2. 서버는 성공/충돌 시 어떤 필드를 돌려줘야 프론트가 바로 다음 상태로 넘어갈 수 있는가
3. queue는 어떤 자료구조와 상태 전이로 관리하는 것이 안전한가

## 현재 추천 방향

- request는 `clientId`, `batchId`, `operations`를 기본으로 둔다.
- 기존 block을 바꾸는 operation은 `version`을 필수로 둔다.
- 새 block은 request에서 `blockRef=tempId`로 참조하고, 성공 응답에서 `tempId -> blockId` 매핑을 돌려준다.
- `BLOCK_CREATE`는 위치 필드와 함께 선택적 `content`를 받을 수 있다.
- transaction은 전체 rollback이다.
- conflict 응답은 충돌 block의 최신 `version`, 최신 `content`를 포함한다.
- frontend queue는 단순 FIFO가 아니라 coalescing queue로 관리한다.
- flush는 debounce만이 아니라 `max autosave interval`과 명시적 flush 트리거를 함께 사용한다.

## 관련 문서

- [014-adopt-transaction-centered-editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/014-adopt-transaction-centered-editor-save-model.md)
- [020-allow-optional-content-on-block-create-in-transactions.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/020-allow-optional-content-on-block-create-in-transactions.md)
- [2026-03-20-editor-save-api-boundary-and-transaction-design.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md)

## Request DTO 초안

### 엔드포인트

`POST /v1/documents/{documentId}/transactions`

### 상위 필드

```json
{
  "clientId": "web-editor",
  "batchId": "8dc6d124-cc41-4b7e-8db3-f4d458f7f3f0",
  "operations": []
}
```

### 필드 설명

- `clientId`
- 같은 사용자의 여러 브라우저 탭/세션을 구분하기 위한 클라이언트 식별자

- `batchId`
- 이 저장 요청 자체의 식별자
- stale 응답 무시, 디버깅, 재시도 구분에 사용

- `operations`
- 순서가 보장되는 operation 배열

## Operation DTO 초안

### 1. `BLOCK_CREATE`

```json
{
  "opId": "op-1",
  "type": "BLOCK_CREATE",
  "blockRef": "tmp:block:1",
  "content": {
    "format": "rich_text",
    "schemaVersion": 1,
    "segments": [
      {
        "text": "새 블록",
        "marks": []
      }
    ]
  },
  "parentRef": null,
  "afterRef": null,
  "beforeRef": null
}
```

설명:

- `BLOCK_CREATE`의 `blockRef` 값은 클라이언트 로컬 식별자인 `tempId`다.
- 서버는 `tempId`를 그대로 저장하지 않고, 성공 시 실제 `blockId`를 생성해 응답에서 매핑을 돌려준다.
- 위치 참조 필드는 `parentRef`, `afterRef`, `beforeRef`를 사용한다.
- `parentRef`, `afterRef`, `beforeRef`도 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`를 담는다.
- `content`는 선택 필드다.
- `content`가 없으면 서버는 empty structured content fallback으로 새 블록을 생성한다.
- `content`가 있으면 서버는 그 값을 새 블록의 초기 content로 저장한다.

### 2. `BLOCK_REPLACE_CONTENT`

기존 block 수정:

```json
{
  "opId": "op-2",
  "type": "BLOCK_REPLACE_CONTENT",
  "blockRef": "real-block-id",
  "version": 5,
  "content": {
    "format": "rich_text",
    "schemaVersion": 1,
    "segments": [
      {
        "text": "수정된 내용",
        "marks": []
      }
    ]
  }
}
```

새 block 수정:

```json
{
  "opId": "op-3",
  "type": "BLOCK_REPLACE_CONTENT",
  "blockRef": "tmp:block:1",
  "content": {
    "format": "rich_text",
    "schemaVersion": 1,
    "segments": [
      {
        "text": "새 블록",
        "marks": []
      }
    ]
  }
}
```

설명:

- 기존 block이면 `version` 필수
- 대상 식별자는 `blockRef`를 사용한다.
- 같은 batch 안에서 생성한 block이면 `blockRef`에 `tempId`를 넣을 수 있다.
- 같은 batch 안에서 생성한 block이 아니라면 `blockRef`에는 실제 `blockId`가 들어가야 한다.
- 프론트는 새 temp block의 `BLOCK_CREATE + BLOCK_REPLACE_CONTENT`를 flush 전에 `BLOCK_CREATE(content=latestContent)` 하나로 접는 것을 우선 권장한다.
- 다만 이 coalescing은 권장 최적화이지, 서버 계약의 필수 전제는 아니다.
- 서버는 temp block을 참조하는 `BLOCK_REPLACE_CONTENT`도 계속 허용해, 프론트가 collapse하지 못한 batch도 정상 처리할 수 있어야 한다.

### 3. `BLOCK_MOVE`

```json
{
  "opId": "op-4",
  "type": "BLOCK_MOVE",
  "blockRef": "real-block-id",
  "version": 3,
  "parentRef": "new-parent-id",
  "afterRef": "block-a",
  "beforeRef": "block-b"
}
```

설명:

- 위치 참조는 `parentRef`, `afterRef`, `beforeRef`를 사용한다.
- `parentRef`, `afterRef`, `beforeRef`도 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`를 담는다.
- v1은 temp parent와 temp sibling anchor를 허용한다.

### 4. `BLOCK_DELETE`

```json
{
  "opId": "op-5",
  "type": "BLOCK_DELETE",
  "blockRef": "real-block-id",
  "version": 4
}
```

설명:

- 지정 block을 루트로 하는 subtree soft delete
- 중복 subtree는 프론트 queue 단계에서 정리

## Success Response DTO 초안

```json
{
  "documentId": "doc-id",
  "batchId": "8dc6d124-cc41-4b7e-8db3-f4d458f7f3f0",
  "appliedOperations": [
    {
      "opId": "op-1",
      "status": "APPLIED",
      "tempId": "tmp:block:1",
      "blockId": "real-block-id",
      "version": 0,
      "sortKey": "000000000003000000000000"
    },
    {
      "opId": "op-3",
      "status": "APPLIED",
      "blockId": "real-block-id",
      "version": 2
    },
    {
      "opId": "op-4",
      "status": "APPLIED",
      "blockId": "another-block-id",
      "version": 4,
      "sortKey": "000000000002500000000000"
    },
    {
      "opId": "op-5",
      "status": "APPLIED",
      "blockId": "delete-root-id",
      "deletedAt": "2026-03-20T18:30:00"
    }
  ]
}
```

### 성공 응답에서 프론트가 필요한 값

- `batchId`
- 현재 in-flight batch와 일치하는지 확인

- `tempId`, `blockId`
- 새 block 매핑 반영

- `version`
- 이후 수정 충돌 검증 기준 업데이트

- `sortKey`
- 이동/생성 후 로컬 정렬 상태 업데이트

- `deletedAt`
- 삭제 확정 상태 반영

## Conflict Response DTO 초안

```json
{
  "code": "CONCURRENT_MODIFICATION",
  "message": "다른 변경이 먼저 반영되었습니다.",
  "batchId": "8dc6d124-cc41-4b7e-8db3-f4d458f7f3f0",
  "conflicts": [
    {
      "opId": "op-2",
      "type": "BLOCK_REPLACE_CONTENT",
      "blockId": "real-block-id",
      "version": 5,
      "actualVersion": 6,
      "serverBlock": {
        "id": "real-block-id",
        "version": 6,
        "content": {
          "format": "rich_text",
          "schemaVersion": 1,
          "segments": [
            {
              "text": "서버 최신 내용",
              "marks": []
            }
          ]
        }
      }
    }
  ]
}
```

### 충돌 응답에서 프론트가 필요한 값

- 어떤 `opId`가 실패했는지
- 어떤 `blockId`가 충돌했는지
- `version`, `actualVersion`
- 최신 서버 block content

추가 원칙:

- conflict 응답을 받았다고 해서 프론트의 로컬 draft와 pending 전체를 즉시 폐기하지 않는다.
- 실패한 in-flight batch는 종료 처리하되, 로컬 draft는 유지한다.
- 프론트는 최신 서버 block 정보와 로컬 draft를 함께 들고 있다가 다음 pending을 다시 조립할 수 있어야 한다.
- 다음 pending 재조립 기준은 실패한 batch 원문 복사가 아니라 현재 로컬 문서 상태다.
- 같은 실패 batch 안의 non-conflict operation도 서버에는 미반영이므로, 로컬 상태가 유지되면 다시 pending에 포함될 수 있다.

## Frontend Queue 자료구조 초안

### 문서 상태

```text
EditorDocumentState
- documentId
- blocksById
- rootBlockIds
- tempIdMap
- pendingOperations
- inFlightBatch
- dirtyWhileInFlight
- saveStatus
```

### Pending operation 예시

```text
PendingOperation
- opId
- type
- blockRef
- version?
- payload
```

여기서 `blockRef`는 실제 `blockId`일 수도 있고 `tempId`일 수도 있다.

## Queue Coalescing 규칙

기본 원칙:

- coalescing과 상쇄는 클라이언트 queue 단계에서 수행한다.
- 서버는 클라이언트가 정리해 보낸 최종 operation 집합을 검증하고 반영한다.
- 서버가 사용자의 원래 편집 순서를 받아 다시 재조립하는 모델은 v1 범위에 포함하지 않는다.

### 1. content는 마지막 상태만 유지

- 같은 block에 대해 여러 `BLOCK_REPLACE_CONTENT`가 있으면 마지막 것 하나만 남긴다.

### 2. create 후 delete는 상쇄

- flush 전 새 block이 삭제되면
- `BLOCK_CREATE`
- 해당 block의 `BLOCK_REPLACE_CONTENT`
- 해당 block의 `BLOCK_MOVE`
- 모두 제거한다.

### 3. delete 뒤 후속 수정 제거

- delete 대상 block의 이후 `replace_content`, `move`는 제거한다.

### 4. move는 마지막 위치만 유지

- 같은 block의 연속 move는 마지막 위치 기준으로 정리할 수 있다.

### 5. delete는 루트 기준 정규화

- 부모 subtree delete가 있으면 자식 delete는 제거한다.

## Flush 상태 모델

### 상태

- `idle`
- `scheduled`
- `in_flight`
- `dirty_while_in_flight`

### 전이 예시

1. 편집 발생 -> `scheduled`
2. debounce 만료 -> `in_flight`
3. 응답 전 새 편집 발생 -> `dirty_while_in_flight`
4. 응답 성공
- pending 없음 -> `idle`
- pending 있음 -> 즉시 다음 flush

### flush 트리거

- debounce 만료
- max autosave interval 도달
- `Ctrl+S`
- page leave

## 프론트 전처리 체크리스트

자세한 이벤트 흐름 예시는 `docs/guides/frontend-editor-transaction-implementation-guide.md`의 "프론트 전처리 시나리오"를 기준으로 본다.

### 공통

- 로컬 block tree 먼저 갱신
- operation queue 적재
- coalescing 적용
- flush 필요 여부 재계산

### create

- tempId 발급
- 로컬 block 삽입
- `BLOCK_CREATE` 적재

### replace_content

- 로컬 content 갱신
- 마지막 `replace_content`만 유지

### move

- 로컬 위치 갱신
- 같은 block의 이전 move 정리

### delete

- 로컬 subtree 제거
- 부모/자식 delete 중복 정리
- flush 전 새 block이면 create와 상쇄

### flush 직전

- debounce 기준 충족 여부 확인
- max interval 기준 충족 여부 확인
- in-flight 여부 확인
- 최종 batch 조립

## 백엔드 처리 체크리스트

### 공통

- document 활성 상태 확인
- request/operation validation
- DB transaction 시작

### create

- parent/document/depth 검증
- sortKey 계산
- tempId 매핑 등록

### replace_content

- real block 또는 temp block 해석
- real block이면 version 검증
- content validation

### move

- 대상 block 조회
- version 검증
- 위치 정합성 검증
- sortKey 재계산

### delete

- 대상 block 조회
- version 검증
- subtree 수집
- soft delete 반영

### 마무리

- 실패 시 전체 rollback
- success/conflict 응답 조립

## 추천 구현 포인트

### 서버 DTO 구현자

- 서버는 coalescing 엔진이 아니라 validation + apply 계층이다.
- `blockRef`가 실제 `blockId`인지 `tempId`인지 명시적으로 파싱해야 한다.
- 전체 rollback이므로 operation 순회 중 하나라도 실패하면 commit하지 않는다.
- conflict 응답에는 최신 `content`를 반드시 담는다.

### 프론트 구현자

- 프론트가 상쇄와 최종 batch 조립을 담당한다.
- queue는 단순 배열이 아니라 정리 규칙이 있는 store여야 한다.
- `batchId` 없이 응답을 적용하면 stale 응답에 취약하다.
- `tempId` 매핑은 flush 성공 직후 가장 먼저 반영해야 한다.
- 로컬 변경은 conflict가 나도 바로 버리면 안 된다.

## 미해결 쟁점

1. `BLOCK_MOVE`의 anchor에 같은 batch 안의 `tempId`를 허용할지
2. delete conflict 응답에 최신 subtree 요약이 필요한지
3. create 직후 empty content인 block은 flush 전에 생략 가능한지

## 다음 액션

1. 위 DTO 초안을 서버 request/response 클래스로 구체화한다.
2. 프론트 queue 자료구조와 상태 머신을 실제 store 설계로 내린다.
3. tempId 참조 규칙과 anchor 규칙을 테스트 케이스로 고정한다.
