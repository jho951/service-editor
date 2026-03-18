# 2026-03-18 블록 저장 API 전략 검토 메모

## 문서 목적

- 블록 기반 문서 편집기의 저장 전략 후보를 비교한다.
- 자동 저장, 수동 저장, 향후 다중 사용자 협업까지 고려한 API 설계 방향을 정리한다.
- 현재 저장소의 `block tree`, `sortKey`, `version` 전제를 기준으로 실무적인 추천안을 제시한다.

## 배경

- 현재 요구사항은 문서 콘텐츠를 ordered block tree로 저장한다.
- 블록은 `parentId`, `sortKey`, `version`을 가진다.
- 현재 블록 타입은 `TEXT` 하나이고, 본문은 plain string이다.
- 이미 단건 생성/조회 API와 `sortKey` 기반 삽입 정책이 존재한다.

## 검토 범위

- 블록 저장 전략 비교
- autosave와 manual save의 관계 정리
- 향후 협업 확장성을 고려한 저장 API 방향 제안

## 고려한 자료와 사례

- Notion은 클라이언트에서 변경을 먼저 로컬 상태에 적용하고, 여러 변경을 transaction으로 묶어 `/saveTransactions`로 전송한다. 로컬 캐시와 전송 큐를 분리해 저장 실패나 오프라인도 견딘다.
- Notion은 블록을 ordered set/array와 parent 관계로 다루며, 한 번의 사용자 액션이 여러 record 변경으로 확장될 수 있음을 전제로 한다.
- ProseMirror 협업 가이드는 중앙 authority가 현재 version을 관리하고, 클라이언트는 자신이 본 version 기준으로 steps를 보내며, 충돌 시 rebase 후 재전송하는 모델을 제시한다.
- Yjs는 변경을 incremental update로 다루고, update가 commutative, associative, idempotent 하도록 설계한다.
- Figma는 authoritative multiplayer 서버를 두고 validation, ordering, conflict resolution을 처리하며, checkpoint와 journal을 함께 운영한다.

## 현재 결론

- 현 시점 1순위 추천은 `클라이언트 로컬 반영 + debounce 자동 저장 + batch patch API + optimistic version 충돌 제어`다.
- 자동 저장과 `Ctrl+S`는 서로 다른 API가 아니라 같은 저장 큐를 flush하는 두 가지 트리거로 보는 것이 좋다.
- 진짜 실시간 공동 편집이 핵심 목표가 되기 전까지는 CRDT/OT 전면 도입보다, batch patch와 버전 충돌 제어를 먼저 안정화하는 편이 더 실무적이다.

## 선택지

### 선택지 1. 문서 전체 Snapshot 저장

#### 개요

- 클라이언트가 현재 문서의 전체 block tree를 메모리에서 관리한다.
- 1~2초 debounce 자동 저장 또는 `Ctrl+S` 시 전체 스냅샷을 서버에 보낸다.
- 서버는 전체 payload를 기준으로 diff 또는 replace-upsert를 수행한다.

#### 권장 API 형태

`PUT /v1/documents/{documentId}/content`

요청 예시:

```json
{
  "baseVersion": 42,
  "clientRequestId": "4d617a0c-5f34-4ee3-8d97-2c363a11f6ae",
  "blocks": [
    {
      "id": "b1",
      "parentId": null,
      "type": "TEXT",
      "text": "제목",
      "sortKey": "000000000001000000000000",
      "deleted": false
    },
    {
      "id": "b2",
      "parentId": "b1",
      "type": "TEXT",
      "text": "본문",
      "sortKey": "000000000002000000000000",
      "deleted": false
    }
  ]
}
```

응답 예시:

```json
{
  "documentId": "doc-uuid",
  "acceptedVersion": 43,
  "savedAt": "2026-03-18T16:20:00",
  "changedBlockCount": 2
}
```

#### 시나리오

1. 사용자가 블록 텍스트를 수정한다.
2. 프론트는 즉시 로컬 상태를 갱신한다.
3. debounce 타이머가 만료되거나 `Ctrl+S`가 눌리면 현재 문서 전체를 전송한다.
4. 서버는 `baseVersion`이 현재 문서 version과 같은지 확인한다.
5. 같으면 전체 block tree 정합성 검증 후 한 트랜잭션으로 반영한다.
6. 다르면 `409`를 반환하고, 클라이언트는 최신 content를 다시 받아 재적용해야 한다.

#### 장점

- 구현이 가장 단순하다.
- 서버 쪽 검증 로직을 한 곳에 모으기 쉽다.
- 버전 히스토리 스냅샷 저장과 궁합이 좋다.

#### 단점

- payload가 빠르게 커진다.
- 작은 수정도 전체 문서를 다시 보내게 된다.
- 충돌 단위가 문서 전체라 다중 사용자 편집에서 불편하다.
- 부분 실패 복구, 세밀한 감사 로그, 작업 단위 undo/redo와의 궁합이 약하다.

#### 적합한 상황

- 초기 제품
- 문서 크기가 작고 단일 사용자 비중이 높은 경우
- 협업보다 구현 속도가 더 중요한 경우

### 선택지 2. Block Patch Batch 저장

#### 개요

- 클라이언트는 편집 이벤트를 즉시 로컬 적용한다.
- 서버 전송은 block 단위 patch operation들을 짧은 구간으로 batch 묶어 보낸다.
- 자동 저장과 수동 저장은 모두 같은 batch queue를 flush한다.
- 서버는 `baseVersion` 또는 `documentVersion` 기준으로 검증하고, 성공 시 새 version과 적용 결과를 돌려준다.

#### 권장 API 형태

`POST /v1/documents/{documentId}/transactions`

요청 예시:

```json
{
  "clientId": "web-7",
  "batchId": "b7e9b24d-c7b1-4d63-a5fe-fdf1516b7af2",
  "baseVersion": 42,
  "operations": [
    {
      "opId": "op-1",
      "type": "block.update_text",
      "blockId": "b2",
      "version": 5,
      "text": "수정된 본문"
    },
    {
      "opId": "op-2",
      "type": "block.insert",
      "tempId": "tmp-1",
      "parentId": null,
      "afterBlockId": "b2",
      "beforeBlockId": null,
      "block": {
        "type": "TEXT",
        "text": "새 블록"
      }
    },
    {
      "opId": "op-3",
      "type": "block.move",
      "blockId": "b5",
      "version": 3,
      "parentId": "b1",
      "afterBlockId": "b2",
      "beforeBlockId": "tmp-1"
    }
  ]
}
```

응답 예시:

```json
{
  "documentId": "doc-uuid",
  "acceptedVersion": 43,
  "appliedOperations": [
    {
      "opId": "op-1",
      "status": "APPLIED",
      "blockId": "b2",
      "version": 6
    },
    {
      "opId": "op-2",
      "status": "APPLIED",
      "tempId": "tmp-1",
      "blockId": "b9",
      "version": 1,
      "sortKey": "000000000003000000000000"
    },
    {
      "opId": "op-3",
      "status": "APPLIED",
      "blockId": "b5",
      "version": 4,
      "sortKey": "000000000002500000000000"
    }
  ],
  "serverEvents": [
    {
      "type": "block.updated",
      "blockId": "b2"
    },
    {
      "type": "block.created",
      "blockId": "b9"
    },
    {
      "type": "block.moved",
      "blockId": "b5"
    }
  ]
}
```

충돌 응답 예시:

```json
{
  "code": "CONCURRENT_MODIFICATION",
  "message": "문서가 다른 변경으로 먼저 수정되었습니다.",
  "data": {
    "currentVersion": 44,
    "conflicts": [
      {
        "opId": "op-1",
        "blockId": "b2",
        "reason": "BLOCK_VERSION_MISMATCH"
      }
    ]
  }
}
```

#### 시나리오

1. 사용자가 타이핑하거나 블록을 이동한다.
2. 프론트는 즉시 로컬 상태와 로컬 큐에 operation을 기록한다.
3. 300~1000ms debounce로 queue를 batch로 묶는다.
4. `Ctrl+S`, blur, page hide, 일정 주기 flush 시 즉시 전송한다.
5. 서버는 관련 block, parent, sibling만 읽어 before/after를 계산한다.
6. 한 트랜잭션 안에서 operation 순서대로 적용하고, 새 `sortKey`, block version, document version을 갱신한다.
7. 성공 응답을 받은 클라이언트는 로컬 pending queue를 정리한다.
8. 같은 batch 결과를 WebSocket/SSE로 다른 참여자에게 브로드캐스트할 수 있다.

#### 장점

- 네트워크 효율이 좋다.
- 충돌 범위를 문서 전체가 아니라 block 또는 batch 수준으로 줄일 수 있다.
- 현재 저장소의 `sortKey`, `version`, 단건 block API와 자연스럽게 이어진다.
- 추후 실시간 협업 브로드캐스트를 얹기 좋다.
- 감사 로그, undo 단위, 부분 재시도, idempotency 설계가 수월하다.

#### 단점

- snapshot보다 설계 복잡도가 높다.
- 프론트에 local queue, retry, dedupe, temp ID 치환이 필요하다.
- 텍스트 동시 편집이 심해지면 block 내부 text 자체는 여전히 충돌 부담이 남는다.

#### 적합한 상황

- 블록 기반 문서 편집기 대부분의 현실적인 1차 목표
- 자동 저장이 필요하지만 CRDT까지는 아직 이른 단계
- 추후 다중 사용자 협업 가능성을 미리 열어두고 싶은 경우

#### 현재 저장소 기준 추천 상세

- 가장 추천하는 저장 API다.
- 기존 `PATCH /v1/blocks/{blockId}`는 단건 명령 API로 유지하고, 편집기 저장 경로는 별도 `transactions` API로 두는 편이 좋다.
- block text 수정, block 생성, block 이동, block 삭제를 모두 operation으로 표준화한다.
- `batchId`로 idempotency를 보장한다.
- 충돌 판단은 최소 두 층으로 둔다.
- 문서 단위 `baseVersion`
- 개별 block 단위 `version`
- 브라우저 단에서는 `autosave`와 `Ctrl+S`를 별도 의미로 보지 말고 동일 queue flush로 통합한다.

### 선택지 3. 실시간 Operation Stream 또는 CRDT 저장

#### 개요

- 문서 편집 상태를 실시간 동기화 대상으로 본다.
- REST는 초기 로드, snapshot, 복구에 쓰고, 실제 편집 전파는 WebSocket 중심으로 처리한다.
- 구현 방식은 크게 두 갈래다.
- 중앙 authority + versioned steps/operations
- CRDT update 기반 동기화

#### 권장 API 형태 A. 중앙 authority step 모델

`POST /v1/documents/{documentId}/steps`

요청 예시:

```json
{
  "clientId": "web-7",
  "baseVersion": 42,
  "steps": [
    {
      "stepId": "s-1",
      "type": "replace_text_range",
      "blockId": "b2",
      "from": 5,
      "to": 7,
      "text": "저장"
    }
  ]
}
```

응답 예시:

```json
{
  "acceptedVersion": 43,
  "appliedStepIds": [
    "s-1"
  ]
}
```

#### 권장 API 형태 B. CRDT update 모델

`POST /v1/documents/{documentId}/collab/updates`

요청 예시:

```json
{
  "clientId": "web-7",
  "encoding": "base64",
  "update": "AAECAwQF"
}
```

응답 예시:

```json
{
  "status": "ACCEPTED",
  "serverClock": 9912
}
```

#### 시나리오

1. 문서 진입 시 클라이언트는 snapshot과 현재 version 또는 state vector를 가져온다.
2. 편집 중에는 작은 단위의 step/update를 실시간으로 서버에 보낸다.
3. 서버는 authority 역할로 ordering, validation, rebasing 또는 merge를 처리한다.
4. 서버는 승인된 변경을 다른 참여자에게 즉시 브로드캐스트한다.
5. 일정 주기마다 checkpoint/snapshot을 남기고, 중간 변경은 journal/update log로 보관한다.
6. 재접속 클라이언트는 최신 snapshot + 이후 로그만 받아 빠르게 복구한다.

#### 장점

- 동시 편집 경험이 가장 좋다.
- 커서, selection, presence, live sync와 잘 맞는다.
- 오프라인 편집과 재연결 복구까지 확장하기 좋다.

#### 단점

- 구현 난도가 가장 높다.
- 텍스트 범위 연산, rebase, binary update 저장, snapshot compaction, awareness 채널까지 필요하다.
- 현재 plain text block 수준에서는 비용 대비 효과가 과할 수 있다.
- 운영 난이도도 높다.

#### 적합한 상황

- 여러 사용자가 같은 블록을 자주 동시에 편집하는 것이 핵심 가치일 때
- 협업 경험이 제품 차별화 요소일 때
- 전용 실시간 인프라를 감당할 수 있을 때

## 어떤 방식이 실무에서 주로 쓰이나

- 단일 사용자 또는 협업이 약한 CMS/에디터는 snapshot 또는 단건 PATCH가 흔하다.
- Notion 류의 block editor는 transaction batch 모델이 매우 실무적이다.
- Google Docs, Figma, Yjs 기반 에디터처럼 강한 동시 편집이 핵심이면 step/OT/CRDT 계열이 쓰인다.
- 실제 대형 제품은 보통 하나만 쓰지 않는다.
- 평소에는 incremental change 전송
- 백엔드/스토리지에는 journal + snapshot/checkpoint 병행
- 클라이언트에는 local cache + retry queue 병행

## 추천안

### 지금 당장

- 전략 2를 채택한다.
- 저장 트리거는 `debounce autosave + manual flush(Ctrl+S)` 조합으로 간다.
- API는 `POST /v1/documents/{documentId}/transactions` 형태로 분리한다.

### 이유

- 현재 도메인이 block tree이고, 이미 `sortKey`, `version`, 단건 block create/read가 있어서 확장 경로가 자연스럽다.
- snapshot보다 협업 확장성이 좋고, CRDT보다 훨씬 빨리 안정화할 수 있다.
- 이후 WebSocket 브로드캐스트를 붙여도 API 자산을 버리지 않는다.

## 구현 시 꼭 넣을 것

- `batchId` 기반 멱등성
- `baseVersion` 충돌 감지
- block 단위 `version` 충돌 감지
- temp ID -> real ID 매핑 응답
- 실패 batch 재시도 정책
- `pagehide` 또는 `visibilitychange` 시 flush
- 문서 진입 시 snapshot 조회 API와 저장 API 분리
- 서버 내부 journal 또는 audit log
- 장기적으로 snapshot compaction 또는 revision history

## 추후 확장 순서

1. 단건 `PATCH /v1/blocks/{blockId}` 구현
2. `POST /v1/documents/{documentId}/transactions` 추가
3. 클라이언트 local queue + debounce autosave 적용
4. WebSocket으로 다른 사용자에게 저장 결과 브로드캐스트
5. block 내부 text 충돌이 커지면 step 기반 또는 CRDT 기반으로 확장

## 관련 문서

- [블록 저장 API 검토 메모](/home/ghmin/project/ai_project/Block-server/docs/discussions/2026-03-18-block-save-api-review.md)
- 작업 로그: `prompts/2026-03-18-block-save-api-strategy.md`

## 참고 자료

- Notion, The data model behind Notion's flexibility: https://www.notion.com/blog/data-model-behind-notion
- ProseMirror Guide, Collaborative editing: https://prosemirror.net/docs/guide/
- Yjs Docs, Document Updates: https://docs.yjs.dev/api/document-updates
- Yjs Docs, Y.Doc API: https://docs.yjs.dev/api/y.doc
- Figma, Making multiplayer more reliable: https://www.figma.com/blog/making-multiplayer-more-reliable/
- Figma, How Figma’s multiplayer technology works: https://www.figma.com/blog/how-figmas-multiplayer-technology-works/
- Figma, Building Figma’s code layers: https://www.figma.com/blog/building-figmas-code-layers/
