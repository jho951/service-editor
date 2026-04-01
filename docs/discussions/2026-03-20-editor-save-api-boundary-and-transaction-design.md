# 2026-03-20 에디터 저장 API 책임 분리 및 transaction 설계 메모

## 문서 목적

- v1 에디터 저장 기능에서 각 API가 무엇을 담당해야 하는지 경계를 정리한다.
- `save`와 일반 단건 API의 책임 충돌을 줄인 상태로 `POST /v1/documents/{documentId}/transactions` 초안을 설계한다.
- autosave, `Ctrl+S`, page leave flush, 단일/다중 이동, subtree 삭제 시나리오를 같은 저장 모델로 설명한다.
- 블록 생성은 단건 생성 API와 transaction operation 중 어디에 두는 것이 더 자연스러운지 정리한다.

## 배경

- 현재 저장소에는 블록 목록 조회, 블록 생성, 블록 수정, 블록 삭제 API가 이미 존재한다.
- 최근 논의에서 에디터 저장의 주 경로는 `debounce + batch patch + optimistic lock`으로 잡혔다.
- TEXT 블록 본문 canonical source는 plain text가 아니라 structured content JSON이다.
- v1 undo/redo는 서버가 아니라 브라우저 세션 캐시에서 처리하기로 했다.
- 사용자는 단일 블록 이동, 여러 블록 이동, subtree 삭제, 연속 타이핑을 모두 "저장"이라는 같은 사용자 경험으로 인식한다.

## 검토 범위

- 에디터 저장과 일반 단건 API의 책임 분리
- v1에서 남길 API와 주 경로로 사용할 API 정의
- `transactions` 요청/응답 모델 초안

## 핵심 질문

1. 에디터 저장은 어떤 API를 표준 경로로 사용해야 하는가
2. 기존 `PATCH /v1/blocks/{blockId}`와 `DELETE /v1/blocks/{blockId}`는 어떤 역할만 남겨야 하는가
3. structured content JSON과 debounce 저장을 함께 둘 때 v1 operation granularity는 어디까지가 적절한가
4. 블록 생성을 transaction에 포함한다면 생성 operation과 content 저장 operation을 어떻게 나눌 것인가

## 고려한 자료와 사례

- `docs/discussions/2026-03-18-block-save-api-strategy.md`
- `docs/discussions/2026-03-18-block-save-api-review.md`
- `docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md`
- `docs/discussions/2026-03-19-block-structured-content-strategy.md`
- `docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md`
- 현재 `BlockController`, `BlockService`, `docs/REQUIREMENTS.md`

## 선택지

### 선택지 1. 기존 단건 블록 API 중심 저장

#### 개요

- 본문 수정은 `PATCH /v1/blocks/{blockId}`
- 이동은 `POST /v1/blocks/{blockId}/move`
- 삭제는 `DELETE /v1/blocks/{blockId}`
- autosave는 위 단건 API를 debounce 후 연속 호출

#### 시나리오

1. 사용자가 블록 3개를 수정하고 블록 2개를 이동한다.
2. 프론트는 debounce 만료 후 수정 API 3번, 이동 API 2번을 각각 보낸다.
3. 중간에 일부 요청만 실패하면 클라이언트가 부분 재시도와 정합성 복구를 직접 처리한다.

#### 장점

- 기존 REST API를 그대로 활용하기 쉽다.
- Postman, 수동 디버깅, 운영 툴에서는 직관적이다.

#### 단점

- 에디터 저장 흐름이 여러 API로 찢어진다.
- 단일 이동, 다중 이동, 다중 삭제, 본문 수정이 하나의 "저장"으로 묶이지 않는다.
- debounce와 batch 저장을 채택한 이유가 약해진다.

#### 트레이드오프

- 초기 연결은 쉬울 수 있지만, 에디터 저장 큐와 충돌/재시도 로직이 빠르게 복잡해진다.

#### 적합한 상황

- 에디터보다 관리형 CRUD가 중심인 제품

### 선택지 2. 에디터 저장은 transactions 단일 경로, 단건 API는 보조 경로

#### 개요

- 에디터에서 발생한 생성/수정/이동/삭제 저장은 모두 `POST /v1/documents/{documentId}/transactions`로 보낸다.
- `GET /v1/documents/{documentId}/blocks`는 에디터 블록 전체 조회로 사용한다.
- `POST /v1/documents/{documentId}/blocks`와 `DELETE /v1/blocks/{blockId}`는 보조 경로로만 유지한다.
- `PATCH /v1/blocks/{blockId}`는 에디터 주 저장 경로에서 제외하거나 제거 후보로 둔다.

#### 시나리오

1. 사용자가 블록 생성, 본문 수정, 블록 이동, subtree 삭제를 연속해서 수행한다.
2. 프론트는 로컬 상태를 먼저 바꾸고, dirty 상태와 pending operation을 큐에 쌓는다.
3. debounce 만료 또는 `Ctrl+S` 시 `transactions` 한 번으로 전송한다.
4. 서버는 한 요청 안에서 생성 위치 계산, 정합성 검증, version 검증, sortKey 계산, subtree 삭제 정책을 수행한다.
5. 성공 시 반영 결과를 batch 단위로 돌려준다.

#### 장점

- 저장 의미가 하나로 모인다.
- 생성했다가 곧바로 지우는 로컬 편집을 queue 단계에서 상쇄할 수 있다.
- 단일 이동, 다중 이동, 다중 삭제를 같은 저장 큐 모델로 설명할 수 있다.
- structured content의 본문 replace와 구조 변경을 한 경로로 통합할 수 있다.

#### 단점

- transaction API 설계가 필요하다.
- 기존 단건 블록 수정 API와 책임 겹침을 정리해야 한다.

#### 트레이드오프

- API 표면은 조금 늘어나지만, 에디터 저장 모델은 훨씬 명확해진다.

#### 적합한 상황

- autosave와 batch 저장이 중요한 블록 에디터

## 비교 요약

- v1 에디터 저장은 단건 API 연속 호출보다 `transactions` 단일 경로가 더 자연스럽다.
- `GET /v1/documents/{documentId}/blocks`는 현재 에디터 조회 요구를 충분히 만족한다.
- 생성까지 transactions에 포함하면 `+ 버튼`, 엔터, 마크다운 입력으로 생기는 블록도 같은 큐 모델로 설명할 수 있다.
- `DELETE /v1/blocks/{blockId}`는 노션식 명시적 subtree 삭제 액션과 잘 맞지만, 에디터의 일반 autosave 경로까지 맡기면 책임이 섞인다.

## 추천 시나리오

1. 사용자가 새 블록 A를 만든다.
2. 블록 A 본문을 수정한다.
3. 같은 세션에서 블록 B와 C를 다른 위치로 옮긴다.
4. 블록 D를 햄버거 메뉴에서 삭제한다.
5. 프론트는 화면만 먼저 반영하고 operation queue를 갱신한다.
6. debounce 만료 시 `POST /v1/documents/{documentId}/transactions`로 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE`를 한 번에 보낸다.
7. 사용자가 햄버거 메뉴에서 "이 블록 삭제"를 눌렀을 때 즉시 서버 반영이 꼭 필요하면 `DELETE /v1/blocks/{blockId}`를 사용할 수 있다.
8. 다만 에디터 autosave의 표준 경로는 계속 `transactions`로 유지한다.

이 방식이면 사용자는 모든 변경을 "저장" 하나로 인식하고, 서버는 명시적 단일 액션 API도 유지할 수 있다.

## 현재 추천 방향

- 에디터 저장 표준 경로는 `POST /v1/documents/{documentId}/transactions` 하나로 둔다.
- 에디터 조회는 `GET /v1/documents/{documentId}/blocks`를 그대로 사용한다.
- 에디터에서의 블록 생성은 v1 `transactions`의 `BLOCK_CREATE`로 처리한다.
- `POST /v1/documents/{documentId}/blocks`는 비에디터/운영/관리용 보조 API로 남길 수 있다.
- 블록 subtree 삭제는 `DELETE /v1/blocks/{blockId}`를 명시적 단일 액션용으로 유지할 수 있다.
- `PATCH /v1/blocks/{blockId}`는 에디터 저장과 책임이 겹치므로 제거 또는 비주 경로화가 맞다.
- v1 structured content 저장 operation은 세밀한 range patch가 아니라 `BLOCK_REPLACE_CONTENT` 수준으로 제한한다.
- `BLOCK_CREATE`는 위치만 확정하고, 본문은 같은 batch의 `BLOCK_REPLACE_CONTENT`가 담당한다.
- v1 transaction 실패 정책은 partial apply보다 전체 rollback이 더 적합하다.
- 충돌 응답에는 충돌 block의 최신 `version`, 최신 `content`를 포함하는 쪽이 클라이언트 UX에 유리하다.

## API 책임 정리

### `GET /v1/documents/{documentId}/blocks`

- 책임:
- 에디터가 문서의 활성 블록 트리를 한 번에 조회한다.
- soft delete되지 않은 블록만 순서 보장 상태로 반환한다.

- 하지 않는 일:
- 문서 메타데이터 병합 조회
- 저장 상태 계산

### `POST /v1/documents/{documentId}/blocks`

- 책임:
- 비에디터/운영/관리 경로의 새 블록 생성
- 특정 위치 삽입
- 서버 기본값으로 빈 TEXT 블록 materialize

- 사용 권장 맥락:
- 운영 툴
- 관리자 보정
- 테스트/디버깅

- 하지 않는 일:
- 에디터 autosave batch 저장의 표준 경로
- 사용자가 편집 중인 블록 생성/수정 흐름을 queue와 같은 의미로 저장

### `DELETE /v1/blocks/{blockId}`

- 책임:
- 지정 루트 블록과 하위 블록 subtree soft delete
- 노션식 햄버거 메뉴 삭제 같은 명시적 단일 액션 처리

- 사용 권장 맥락:
- 즉시 확정되는 subtree 삭제 액션

- 하지 않는 일:
- 다중 블록 삭제 batch 저장 표준 경로
- 타이핑/이동과 섞인 autosave batch 저장

### `PATCH /v1/blocks/{blockId}`

- 현재 판단:
- 에디터 저장과 책임이 겹친다.
- v1 표준 경로에서 제외하거나 제거 후보로 둔다.

### `POST /v1/documents/{documentId}/transactions`

- 책임:
- 에디터 세션의 저장을 batch 단위로 반영한다.
- debounce autosave, `Ctrl+S`, page leave flush를 같은 저장 모델로 처리한다.
- 여러 블록의 생성, 본문 수정, 단일/다중 이동, 다중 삭제를 한 요청에 담는다.
- block version 기반 낙관적 락 충돌을 batch 컨텍스트에서 검증한다.

- 하지 않는 일:
- 브라우저 undo/redo 저장
- CRDT/OT 수준의 세밀한 동시 수정 병합
- 문서 제목/아이콘/커버 같은 문서 메타데이터 수정
- 브라우저 외부 관리 툴의 단건 CRUD 대체

## v1 transaction 설계 초안

### 엔드포인트

`POST /v1/documents/{documentId}/transactions`

### 요청 필드

```json
{
  "clientId": "web-editor",
  "batchId": "1d2f84b4-1d8f-4b1c-8f15-1d7344f8dc6f",
  "operations": [
    {
      "opId": "op-1",
      "type": "BLOCK_CREATE",
      "blockRef": "tmp-1",
      "parentRef": null,
      "afterRef": null,
      "beforeRef": null
    },
    {
      "opId": "op-2",
      "type": "BLOCK_REPLACE_CONTENT",
      "blockRef": "tmp-1",
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
    },
    {
      "opId": "op-3",
      "type": "BLOCK_MOVE",
      "blockRef": "a2d7a3b0-55e0-44f8-9e19-0d1d01c2f7f0",
      "version": 5,
      "parentRef": null,
      "afterRef": "6e3aef72-4ca4-4c2f-b70c-15f9d3b164c1",
      "beforeRef": null
    },
    {
      "opId": "op-4",
      "type": "BLOCK_DELETE",
      "blockRef": "f67b08c8-99dd-4cde-9ad2-d40bcfa93d5d",
      "version": 2
    }
  ]
}
```

### 요청 필드 의미

- `clientId`
- 클라이언트 인스턴스 식별자

- `batchId`
- 저장 요청 식별자
- in-flight 응답 역전 시 stale ack 무시에 사용

- `opId`
- 각 operation 식별자

- `blockRef`
- request에서 블록을 가리키는 공통 참조값
- 새 블록이면 `tempId`, 기존 블록이면 실제 `blockId`

- `parentRef`, `afterRef`, `beforeRef`
- request에서 위치를 가리키는 공통 참조값
- 같은 batch 안의 새 블록이면 `tempId`, 기존 블록이면 실제 `blockId`

- `version`
- block 단위 optimistic lock 기준값
- 기존 블록에 대한 수정/이동/삭제에만 사용한다.

### v1 허용 operation

- `BLOCK_CREATE`
- 새 빈 TEXT 블록을 특정 위치에 생성
- 위치만 확정하며, 본문은 같은 batch의 `BLOCK_REPLACE_CONTENT`가 담당한다.

- `BLOCK_REPLACE_CONTENT`
- TEXT 블록 본문 structured content 전체 교체
- 대상 필드는 `blockRef`를 사용한다.
- 새 블록이면 `blockRef`에 `tempId`, 기존 블록이면 `blockRef`에 실제 `blockId`를 넣는다.

- `BLOCK_MOVE`
- 단일 블록 위치 이동
- 서버는 `parentRef`, `afterRef`, `beforeRef` 기준으로 새 `sortKey`를 계산한다.
- 위치 ref도 temp parent, temp sibling anchor를 지원한다.

- `BLOCK_DELETE`
- 루트 블록 기준 subtree soft delete

### v1에서 제외하는 operation

- `BLOCK_INSERT_TEXT`
- `BLOCK_DELETE_RANGE`
- `BLOCK_APPLY_MARK`
- `BLOCK_UNDO`
- `BLOCK_REDO`
- 문서 메타데이터 수정 operation

### 응답 예시

```json
{
  "documentId": "f8d6e377-1ef9-4d2c-96f3-8c6a08b2f0aa",
  "batchId": "1d2f84b4-1d8f-4b1c-8f15-1d7344f8dc6f",
  "appliedOperations": [
    {
      "opId": "op-1",
      "status": "APPLIED",
      "tempId": "tmp-1",
      "blockId": "6e3aef72-4ca4-4c2f-b70c-15f9d3b164c1",
      "version": 0,
      "sortKey": "000000000003000000000000"
    },
    {
      "opId": "op-2",
      "status": "APPLIED",
      "blockId": "6e3aef72-4ca4-4c2f-b70c-15f9d3b164c1",
      "version": 4
    },
    {
      "opId": "op-3",
      "status": "APPLIED",
      "blockId": "a2d7a3b0-55e0-44f8-9e19-0d1d01c2f7f0",
      "version": 6,
      "sortKey": "000000000002500000000000"
    },
    {
      "opId": "op-4",
      "status": "APPLIED",
      "blockId": "f67b08c8-99dd-4cde-9ad2-d40bcfa93d5d",
      "deletedAt": "2026-03-20T15:30:00"
    }
  ]
}
```

### 충돌 응답 예시

```json
{
  "code": "CONCURRENT_MODIFICATION",
  "message": "다른 변경이 먼저 반영되었습니다.",
  "batchId": "1d2f84b4-1d8f-4b1c-8f15-1d7344f8dc6f",
  "conflicts": [
    {
      "opId": "op-1",
      "type": "BLOCK_REPLACE_CONTENT",
      "blockId": "6e3aef72-4ca4-4c2f-b70c-15f9d3b164c1",
      "version": 3,
      "actualVersion": 4
    }
  ]
}
```

## 저장 큐 기준 동작

- autosave와 `Ctrl+S`는 서로 다른 API가 아니라 같은 queue flush 트리거다.
- 생성 후 삭제가 같은 flush 전에 일어나면 `BLOCK_CREATE`와 `BLOCK_DELETE`를 queue에서 상쇄할 수 있다.
- debounce 구간 안에서 같은 블록의 연속 본문 수정은 마지막 `BLOCK_REPLACE_CONTENT` 하나로 합친다.
- 아직 전송하지 않은 예약 batch는 새 입력이 오면 교체한다.
- 이미 전송 중인 batch는 취소보다 `batchId` 기준 stale 응답 무시로 처리한다.
- `dirty_while_in_flight` 상태가 있으면 현재 응답 수신 후 다음 batch를 바로 전송한다.

## 대표 시나리오

### 시나리오 1. 새 블록 생성 후 타이핑 autosave

1. 사용자가 새 블록을 만들고 2초 동안 연속 입력한다.
2. 프론트는 로컬에 임시 블록을 만들고 `BLOCK_CREATE`, 마지막 `BLOCK_REPLACE_CONTENT`를 queue에 남긴다.
3. 마지막 입력 후 debounce 만료 시 create와 최종 content만 함께 전송한다.

### 시나리오 2. 블록 여러 개 이동

1. 사용자가 블록 3개를 순서대로 옮긴다.
2. 프론트는 각 이동을 `BLOCK_MOVE`로 queue에 쌓는다.
3. debounce 만료 시 한 batch로 저장한다.

### 시나리오 3. 햄버거 메뉴 단일 subtree 삭제

1. 사용자가 블록 메뉴에서 삭제를 누른다.
2. 즉시 반영이 필요하면 `DELETE /v1/blocks/{blockId}`를 호출한다.
3. 이후 에디터 queue에서 해당 subtree 관련 pending op는 정리한다.

### 시나리오 4. 전체 선택 후 다중 삭제

1. 사용자가 여러 루트 블록을 선택하고 삭제한다.
2. 프론트는 각 루트마다 `BLOCK_DELETE` op를 만든다.
3. debounce 또는 즉시 flush 시 `transactions` 한 번으로 저장한다.

### 시나리오 5. 생성 후 바로 삭제

1. 사용자가 새 블록을 만든다.
2. 아직 flush 전 상태에서 바로 해당 블록을 지운다.
3. 프론트는 `BLOCK_CREATE`와 대응되는 `BLOCK_DELETE`를 queue에서 제거한다.
4. 서버에는 아무 요청도 가지 않을 수 있다.

## 미해결 쟁점

1. `BLOCK_CREATE`가 항상 빈 TEXT 블록만 만들지, 일부 입력 파생 생성에서 초기 content를 허용할지
2. `DELETE /v1/blocks/{blockId}` 호출 직후 `transactions` queue와의 동기화 규칙을 어디까지 서버 계약에 포함할지
3. `PATCH /v1/blocks/{blockId}`를 즉시 제거할지, 내부/테스트 호환을 위해 한동안 유지할지

## 다음 액션

1. 위 경계를 팀 채택안으로 확정한다.
2. 채택 시 `docs/REQUIREMENTS.md`의 블록 API와 저장 정책을 갱신한다.
3. `transactions` 채택이 확정되면 ADR 필요 여부를 다시 판단한다.
4. 구현 전 request/response DTO, validation, 서비스 트랜잭션 정책 초안을 추가로 정리한다.

## 관련 문서

- `docs/discussions/2026-03-18-block-save-api-strategy.md`
- `docs/discussions/2026-03-18-block-save-api-review.md`
- `docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md`
- `docs/discussions/2026-03-19-block-structured-content-strategy.md`
- `docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md`
- [prompts/worklog/2026-03/2026-03-20-editor-transaction-save-model.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-editor-transaction-save-model.md)
