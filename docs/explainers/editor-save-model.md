# Editor Save Model

## 목적

이 문서는 v1 에디터 저장 모델이 어떤 문제를 해결하는지, 왜 블록 단건 API 대신 `save` endpoint 하나로 저장하는지, 그리고 생성/수정/이동/삭제가 어떤 시나리오로 흘러가는지 설명하기 위한 문서다.

대상 범위는 다음과 같다.

- 에디터 표준 읽기/쓰기 API 경계
- `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE`의 역할
- debounce autosave와 `Ctrl+S` flush
- block version 기반 동시성
- 충돌 응답과 복구 방향

관련 문서:

- [2026-03-20-editor-save-api-boundary-and-transaction-design.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md)
- [2026-03-18-block-save-api-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-18-block-save-api-review.md)
- [012-adopt-structured-text-content-and-staged-concurrency-roadmap.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md)

---

## 1. 이 저장 모델이 해결하려는 문제

블록 에디터에서 실제 사용자 행동은 보통 한 가지 API에 딱 맞지 않는다.

예:

- 새 블록 생성
- 바로 내용 입력
- 스타일 적용
- 블록 이동
- 블록 삭제
- 다시 새 블록 생성

이 모든 일이 몇 초 안에 이어질 수 있다.

만약 이 흐름을

- `POST /blocks`
- `PATCH /blocks/{id}`
- `DELETE /blocks/{id}`
- `POST /blocks/{id}/move`

같은 단건 API로 각각 바로 보내면 다음 문제가 생긴다.

- 요청 수가 많아진다.
- 생성 직후 삭제처럼 서버에 보낼 필요 없는 편집도 매번 반영된다.
- autosave와 manual save가 같은 의미로 설명되지 않는다.
- 실패 시 부분 성공과 부분 실패를 클라이언트가 복잡하게 복구해야 한다.

여기서 중요한 점은, 이 문제를 "네트워크 성능"으로만 보면 부족하다는 것이다.

핵심은 모델 일관성이다.

- 사용자는 생성, 수정, 삭제를 따로 구분하지 않고 "계속 편집 중"이라고 느낀다.
- 에디터는 그 연속 편집을 로컬 상태 하나로 관리한다.
- 서버도 그 연속 편집을 가능한 한 같은 저장 모델로 받아야 UI와 서버 의미가 어긋나지 않는다.

그래서 v1 에디터 저장은 "리소스 CRUD"보다 "편집 세션 batch 저장"으로 보는 것이 더 자연스럽다.

핵심 목표는 이 한 줄이다.

> 에디터에서 발생한 변경을 로컬에 먼저 반영하고, 서버에는 의미 있는 최종 변경만 batch로 저장한다.

---

## 2. API 경계

### 에디터가 표준으로 사용하는 API

- `GET /documents/{documentId}/blocks`
- `POST /editor-operations/documents/{documentId}/save`
- `POST /editor-operations/move`

save 요청 top-level은 `clientId`, `batchId`, `operations`를 사용한다.
save 동시성 검사는 batch 전체의 문서 snapshot이 아니라 각 block operation의 `version`으로 처리한다.
save batch 안에 실제 editor 변경이 하나라도 반영되면 응답에는 증가한 최신 `documentVersion`이 내려간다.

move는 문서와 블록 이동을 공통 contract로 처리하는 별도 explicit structure API다.
drag 중간 상태를 저장하지 않고, drop 확정 시점의 최종 위치만 반영한다.

### 보조 API

- `POST /admin/documents/{documentId}/blocks`
- `PATCH /admin/blocks/{blockId}`
- `POST /admin/blocks/{blockId}/move`
- `DELETE /admin/blocks/{blockId}`

보조 API는 남길 수 있다. 다만 에디터의 일반 편집 저장 경로에서는 표준이 아니다.

정리하면:

- 에디터는 읽기 1개, 쓰기 2개를 표준으로 사용한다.
- save는 batch 저장, move는 explicit 구조 변경이라는 서로 다른 write 유스케이스를 맡는다.
- 나머지 단건 블록 API는 운영/관리/디버깅/호환 용도다.

---

## 3. save batch 안의 operation

v1 에디터 표준 operation은 4개만 사용한다.

### `BLOCK_CREATE`

- 새 블록을 특정 위치에 생성한다.
- 역할은 "블록을 트리에 등장시키는 것"이다.
- 요청에서도 블록 참조 필드로 `blockRef`를 사용한다.
- `BLOCK_CREATE`의 `blockRef` 값은 새 블록을 가리키는 `tempId`다.
- 위치 참조는 `parentRef`, `afterRef`, `beforeRef`를 사용한다.
- `parentRef`, `afterRef`, `beforeRef`도 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`다.
- 이 operation은 위치를 항상 다루고, 필요하면 초기 `content`도 함께 받을 수 있다.
- `content`가 없으면 서버는 empty structured content fallback으로 새 블록을 만든다.
- `content`가 있으면 서버는 그 값을 새 블록의 초기 본문으로 저장한다.

### `BLOCK_REPLACE_CONTENT`

- 블록의 structured content 전체를 교체한다.
- 부분 범위 patch가 아니라 블록 본문 전체 저장이다.
- 대상 식별자는 `blockRef`를 사용한다.
- `blockRef`는 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`다.
- 같은 batch 안의 새 block이면 `version`을 보내지 않는다.
- 기존 서버 block이면 클라이언트가 batch를 만들 때 알고 있던 base `version`을 보낸다.

### `BLOCK_MOVE`

- 블록 위치를 옮긴다.
- `parentRef`, `afterRef`, `beforeRef` 기준으로 새 위치를 확정한다.
- 위치 ref도 같은 batch 안의 새 block을 temp 값으로 가리킬 수 있다.
- 같은 batch 안의 새 block이면 `version`을 보내지 않는다.
- 기존 서버 block이면 클라이언트가 batch를 만들 때 알고 있던 base `version`을 보낸다.

### `BLOCK_DELETE`

- 지정 블록을 루트로 하는 subtree를 soft delete 한다.

중요한 점:

- 생성은 `BLOCK_CREATE`
- 기존 블록의 후속 내용 변경은 `BLOCK_REPLACE_CONTENT`

즉 "블록 존재와 위치를 만든다"와 "이미 존재하는 블록의 본문을 다시 바꾼다"를 분리한다.

이 분리가 필요한 이유는 다음과 같다.

- 생성 직후 삭제를 queue에서 상쇄할 수 있다.
- 생성 직후 내용 수정 여러 번도 마지막 content만 남길 수 있다.
- 생성과 내용 저장의 역할이 섞이지 않는다.

즉 create를 save batch에 포함하는 이유는 "요청 수 감소" 하나가 아니라 다음을 함께 만족하기 위해서다.

- 로컬 편집 흐름과 서버 저장 흐름을 같은 모델로 맞춘다.
- 생성 후 수정, 생성 후 삭제, 생성 후 이동 같은 연속 편집을 하나의 batch로 설명한다.
- 의미 없는 중간 상태를 서버에 남기지 않는다.
- Enter, Backspace, 마크다운 파싱 같은 editor interaction을 CRUD 버튼 클릭이 아니라 operation 흐름으로 해석한다.

---

## 4. 전체 흐름

### 4.1 사용자가 새 블록을 만들고 입력할 때

1. 사용자가 엔터나 `+` 버튼으로 새 블록을 만든다.
2. 프론트는 로컬에서 임시 블록을 화면에 먼저 그린다.
3. queue에는 우선 `BLOCK_CREATE(blockRef=tempId)`를 넣는다.
4. 사용자가 debounce 전에 바로 입력하면 프론트는 이를 `BLOCK_CREATE(blockRef=tempId, content=latestContent)` 하나로 접을 수 있다.
5. 필요하면 temp block을 참조하는 `BLOCK_REPLACE_CONTENT`를 함께 보낼 수도 있다.
6. debounce 만료 또는 `Ctrl+S` 시 queue를 `save` 한 요청으로 보낸다.
7. 서버는 실제 `blockId`, `sortKey`, `version`을 만들고 응답으로 돌려준다.
8. 클라이언트는 `tempId -> blockId`를 매핑한다.

즉 서버보다 UI가 먼저 반응한다.
여기서 `tempId`는 클라이언트 로컬 식별자일 뿐이고, DB에는 서버가 만든 실제 `blockId`가 저장된다.
또한 서버는 `BLOCK_CREATE.content`가 없을 때 not null 제약을 만족시키기 위해 기본 empty structured content를 넣을 수 있다.
이 기본값은 새 블록 초기 content의 fallback이다.
또한 temp 참조 해석은 `blockRef`뿐 아니라 `parentRef`, `afterRef`, `beforeRef`까지 같은 request 순서 컨텍스트에서 처리한다.

---

### 4.2 사용자가 생성 후 바로 지울 때

1. 사용자가 새 블록을 만든다.
2. 아직 flush 전 상태에서 곧바로 삭제한다.
3. queue 안에는 `BLOCK_CREATE`와 `BLOCK_DELETE`가 모두 생길 수 있다.
4. 이 둘은 서로 상쇄할 수 있으므로 queue 단계에서 제거한다.
5. 결과적으로 서버에는 아무 요청도 가지 않을 수 있다.

이 시나리오가 바로 "왜 생성도 save batch 안에 넣는가"를 가장 잘 보여준다.

단건 create API를 바로 호출하는 구조였다면, 서버는 불필요한 create/delete를 계속 받았을 것이다.

---

### 4.3 사용자가 Enter로 다음 블록을 만들 때

1. 사용자가 현재 블록을 편집하다가 Enter를 누른다.
2. 에디터는 현재 블록의 최종 content를 로컬에 확정한다.
3. 동시에 다음 위치에 새 임시 블록을 로컬에 만든다.
4. queue에는 현재 블록의 `BLOCK_REPLACE_CONTENT`와 새 블록의 `BLOCK_CREATE`가 함께 들어간다.
5. 사용자가 새 블록에 바로 입력하면 그 블록의 `BLOCK_REPLACE_CONTENT`가 이어서 들어간다.
6. flush 시 서버는 "앞 블록 저장 + 다음 블록 생성 + 다음 블록 내용 저장"을 하나의 연속 편집으로 이해한다.

이 흐름을 create API와 save API로 강하게 나누면, 사용자는 한 번 Enter를 눌렀을 뿐인데 클라이언트는 "먼저 create 호출, 그다음 edit 저장" 같은 인위적인 단계로 쪼개 처리해야 한다.

editor save 모델에서는 Enter가 단지 queue 안에 operation 몇 개를 추가하는 편집 이벤트가 된다.

---

### 4.4 사용자가 블록 맨 앞에서 Backspace로 블록을 지울 때

1. 사용자가 빈 블록의 맨 앞에서 Backspace를 누른다.
2. 에디터는 현재 블록 삭제 또는 이전 블록과 병합 같은 편집 규칙을 먼저 로컬에서 해석한다.
3. 삭제가 맞는 경우 queue에는 `BLOCK_DELETE`가 들어간다.
4. 만약 그 블록이 아직 flush 전의 새 블록이었다면 `BLOCK_CREATE`와 상쇄될 수 있다.

이 흐름도 delete를 별도 즉시 API로 보내는 것보다 operation으로 다루는 편이 자연스럽다.

이유는 Backspace가 사용자의 입장에서 "삭제 버튼 클릭"이 아니라 "편집 중 발생한 상태 변화"이기 때문이다.

---

### 4.5 사용자가 한 블록에 스타일을 여러 번 적용할 때

1. 사용자가 한 문장을 입력한다.
2. bold, italic, color를 순서대로 적용한다.
3. 프론트는 내부 editor state를 structured JSON으로 유지한다.
4. queue에는 최종 `BLOCK_REPLACE_CONTENT` 하나만 남긴다.
5. flush 시 그 최종 content만 저장한다.

즉 v1에서는 mark 추가를 별도 operation으로 보내지 않는다.
structured JSON이 복잡하더라도 저장 단위는 "그 블록의 최종 content"다.

---

### 4.6 사용자가 블록 여러 개를 옮길 때

1. 사용자가 블록 A, B, C를 여러 위치로 이동한다.
2. 프론트는 각각의 이동을 `BLOCK_MOVE`로 queue에 넣는다.
3. debounce 만료 시 한 번의 `save` 요청으로 저장한다.

---

### 4.7 사용자가 `Ctrl+A` 후 삭제할 때

1. 사용자가 문서 전체를 선택하고 삭제한다.
2. 프론트는 삭제 대상 블록을 루트 subtree 기준으로 정규화한다.
3. 부모 블록과 자식 블록이 함께 선택되었으면 부모만 남기고 자식 delete는 제거한다.
4. 남은 루트마다 `BLOCK_DELETE` operation을 만든다.

같은 원리로, 같은 batch 안에서 방금 만든 temp block을 다시 지우는 경우도 서버는 처리할 수 있다.
다만 이 경우도 프론트는 가능하면 flush 전에 `create -> ... -> delete`를 collapse하는 것이 우선이다.
5. flush 시 한 번의 `save` 요청으로 보낸다.

따라서 문서 전체 삭제는 "항상 delete 1개"가 아니라 "삭제 루트 집합"일 수 있다.

---

## 5. debounce와 flush

autosave와 `Ctrl+S`는 서로 다른 저장 API가 아니다.

둘 다 같은 queue를 flush하는 트리거다.

### debounce

- 사용자가 입력을 멈춘 뒤 일정 시간 뒤 flush

### max autosave interval

- 사용자가 계속 타이핑해 debounce가 계속 밀려도, 일정 주기마다 강제 flush 한다.
- 목적은 "오래 타이핑하면 저장이 아예 안 되는 상태"를 막는 것이다.
- 핵심은 debounce만으로 무한정 저장이 밀리면 안 된다는 점이다.

### 즉시 flush 트리거 예

- `Ctrl+S`
- page leave
- 필요 시 Enter, focus change, 명시적 사용자 액션

핵심은 다음이다.

- 로컬 반영은 즉시
- 서버 반영은 queue flush 시점

즉 create와 delete를 save batch에 넣는 이유는 "서버 호출을 줄이기 위해서"만이 아니다.

- UI는 즉시 반응해야 한다.
- 서버는 편집 결과만 받아야 한다.
- queue는 그 사이에서 중간 상태를 정리해야 한다.
- 장시간 연속 입력 중에도 max interval 기준으로 주기적 flush가 가능해야 한다.

이 역할 분담 때문에 create/delete도 operation으로 들어가는 편이 맞다.

---

## 6. queue가 하는 일

queue는 단순히 operation을 쌓기만 하지 않는다.
의미 없는 중간 변경을 정리한다.

예:

- 같은 블록 content 연속 수정 -> 마지막 `BLOCK_REPLACE_CONTENT`만 유지
- 생성 후 삭제 -> 둘 다 제거
- 삭제된 블록에 대한 이후 move/content 수정 -> 제거
- 같은 블록의 연속 move -> 마지막 위치만 유지 가능

즉 queue는 "현재까지 편집 세션에서 서버에 실제로 반영할 가치가 있는 최종 변경"만 남긴다.

여기서 책임을 분명히 나눠야 한다.

- 클라이언트 책임:
- 로컬 편집 이벤트를 queue에 적재한다.
- 같은 flush 전에 의미 없는 중간 변경을 상쇄/병합한다.
- 최종 batch를 만들어 서버에 보낸다.

- 서버 책임:
- 클라이언트가 정리해 보낸 batch가 유효한지 검증한다.
- `tempId` 참조, version, 위치, subtree 삭제 정합성을 확인한다.
- 하나라도 실패하면 전체 rollback 한다.

즉 "create -> replace_content -> delete" 같은 상쇄는 서버가 재조립하는 것이 아니라, 클라이언트가 flush 전에 정리하는 것이 기본 원칙이다.

---

## 7. 동시성은 어떻게 처리하는가

v1 동시성 기준은 `block.version`이다.

문서 전체 version이 아니라 블록 단위 version으로 본다.

### 왜 block 단위인가

- 서로 다른 블록은 독립적으로 바뀔 수 있다.
- 문서 전체 version 하나로 막으면 충돌 범위가 너무 커진다.

### 같은 블록이 아니면

사용자 A가 블록 X를 수정하고,
사용자 B가 블록 Y를 수정하면
서로 충돌하지 않는다.

### 같은 블록이면

사용자 A와 B가 모두 block X version `3`을 보고 있다고 가정한다.

1. A가 먼저 저장한다.
2. 서버는 X를 version `4`로 올린다.
3. B가 나중에 `version=3`으로 저장한다.
4. 서버는 stale update로 보고 `409 Conflict`를 반환한다.

즉 v1은 같은 블록 내부 비중첩 수정도 block 단위 충돌이다.

다만 같은 batch 안에서 한 block을 여러 번 다루는 경우는 예외가 있다.

- temp block은 create 이후의 최신 version을 서버가 내부 컨텍스트로 이어받는다.
- 기존 서버 block도 첫 참조에서 base `version`으로 동시성을 검증한 뒤, 같은 batch 안의 뒤 operation은 서버가 내부 최신 version으로 이어서 처리한다.
- 대신 같은 real block에 대해 batch 안에서 서로 다른 base `version`을 섞어 보내면 conflict다.
- delete도 root block version을 실제 soft delete query의 조건으로 다시 걸어, 검증 직후 다른 사용자가 먼저 수정한 경우 stale delete가 그대로 통과하지 않게 막는다.
- soft delete bulk update는 삭제되는 subtree의 version도 함께 증가시켜, 이미 열려 있던 update/move 트랜잭션이 삭제된 row의 `deletedAt`을 다시 null로 덮어쓰지 못하게 한다.

---

## 8. 충돌 응답은 어떻게 주는가

v1에서는 다음 방향을 사용한다.

- save batch는 전체 rollback
- conflict 응답은 공통 실패 응답의 `CONFLICT(409)`로 반환
- 최신 block content가 필요하면 프론트가 충돌 후 재조회

### 왜 전체 rollback이 나은가

부분 성공, 부분 실패는 에디터 UX를 매우 어렵게 만든다.

예를 들어 10개 변경 중 7개만 저장되고 3개가 실패하면 사용자는 무엇이 실제 저장되었는지 이해하기 어렵다.
클라이언트도 부분 복구, 부분 재시도, 화면 상태 재정렬이 복잡해진다.

반대로 전체 rollback이면 의미가 단순하다.

- 이번 batch는 저장되었거나
- 저장되지 않았거나

v1에서는 이 단순함이 더 중요하다.

### 왜 최신 block content를 재조회하는가

충돌이 났을 때 클라이언트는 바로 판단 재료가 필요하다.

예:

- 내가 편집하던 로컬 content
- 서버 최신 content
- 어느 block이 충돌했는지

현재 v1 구현은 conflict 응답에 최신 block payload를 직접 담지 않는다.
대신 공통 실패 응답을 유지하고, 프론트가 필요한 block 상태를 재조회한다.

최신 content를 확보하면 바로 다음 동작으로 이어가기 쉽다.

- 충돌 UI 표시
- 최신 값과 로컬 값 비교
- 사용자 확인 후 재적용

충돌 응답에 최신 `version`, 최신 `content`를 직접 포함하는 방식은 v2 conflict 응답 고도화 후보로 둔다.

### 시나리오: 오래 타이핑한 뒤 저장 시 동시성 충돌이 난 경우

가장 많이 걱정하는 상황은 이거다.

- 사용자가 저장하지 않은 상태로 오래 타이핑했다.
- 다른 사용자가 같은 block을 먼저 저장했다.
- 이제 내가 저장하려고 하니 `409 Conflict`가 났다.

이 경우에도 로컬 작업본은 버리면 안 된다.

### 전체 rollback이면 queue가 다 사라지는가

아니다.

전체 rollback은 서버 DB 반영 기준이다.
클라이언트 로컬 draft와 pending queue를 모두 폐기한다는 뜻이 아니다.

예를 들어:

1. 사용자가 저장하지 않은 상태로 한 블록을 길게 편집한다.
2. 다른 사용자가 먼저 같은 블록을 저장한다.
3. 현재 사용자가 flush하면 `409 Conflict`가 난다.
4. 서버는 이번 batch를 반영하지 않는다.
5. 하지만 클라이언트는 내 로컬 content와 pending 상태를 유지한다.
6. 대신 충돌 난 block을 `conflicted` 상태로 표시하고, 필요하면 서버 최신 `version`, `content`를 재조회해 보관한다.
7. 사용자는 비교 후 재적용하거나 수정한 뒤 다시 저장할 수 있다.

즉 버리는 것은 "이번 서버 반영 시도"이지, "사용자 로컬 작업본"이 아니다.

클라이언트는 보통 다음 둘을 분리해서 관리해야 한다.

- 현재 화면에 보이는 로컬 draft
- 마지막 flush 시도에 사용했던 in-flight batch

충돌이 나면 in-flight batch는 실패 처리하지만, 로컬 draft는 남겨 두고 다음 pending을 다시 조립할 수 있어야 한다.
이때 기준은 실패한 batch payload 복원이 아니라, 현재 로컬 문서 상태 전체다.
같은 실패 batch 안에 있던 non-conflict 이동/삭제/수정도 서버에는 미반영이므로, 로컬 상태가 그대로라면 다시 pending에 포함될 수 있다.

---

## 9. 이 모델이 실시간 협업을 의미하는가

아니다.

이 설계는 저장 모델이다.
실시간 브로드캐스트 모델은 아니다.

즉:

- 사용자는 자기 로컬 편집을 즉시 본다.
- 서버에는 flush 시점에 저장된다.
- 다른 사용자가 그 변경을 자동 실시간으로 보는 것은 별도 기능이다.

실시간 협업을 하려면 추가로 필요하다.

- WebSocket/SSE
- 브로드캐스트 이벤트
- 원격 변경 수신 후 로컬 적용 정책

현재 v1은 여기까지 포함하지 않는다.

---

## 10. 구현 시 기억할 핵심 한 줄

이 모델의 핵심은 다음과 같다.

> 생성, 수정, 이동, 삭제를 로컬 queue에 먼저 쌓고, 서버에는 `save` 한 경로로 의미 있는 최종 변경만 저장한다.
