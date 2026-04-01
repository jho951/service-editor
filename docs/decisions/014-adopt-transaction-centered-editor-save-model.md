# ADR 014: 에디터 저장은 transaction 중심 모델을 채택한다

## 상태

채택됨

## 배경

v1 에디터는 structured content 기반 TEXT 블록을 편집한다.

실제 사용자 흐름은 단건 CRUD와 다르다.

- 새 블록 생성
- 본문 입력
- 스타일 적용
- 블록 이동
- 블록 삭제
- 생성 직후 삭제
- `Ctrl+S`
- autosave

이 변경들은 짧은 시간 안에 연속해서 일어난다.

이 흐름을 `POST /blocks`, `PATCH /blocks/{id}`, `DELETE /blocks/{id}` 같은 단건 API로 즉시 보내면 다음 문제가 생긴다.

- 요청 수가 빠르게 늘어난다.
- 생성 직후 삭제처럼 서버에 남길 필요 없는 중간 상태까지 저장된다.
- autosave와 manual save가 같은 의미로 설명되지 않는다.
- 부분 성공/부분 실패가 생기면 프론트 복구가 복잡해진다.
- 에디터의 로컬 편집 흐름과 서버 저장 모델이 어긋난다.

동시에 v1은 실시간 협업 자체보다 먼저 "작동하는 편집기 저장"을 우선해야 한다.

- 본문은 structured content JSON을 사용한다.
- 동시성은 block 단위 optimistic lock으로 시작한다.
- undo/redo는 브라우저 세션 범위에서 처리한다.

이 전제에서 에디터 저장은 리소스 CRUD보다 "편집 세션 batch 저장"으로 보는 편이 더 자연스럽다.

## 결정

- 에디터의 표준 write 경로는 `POST /documents/{documentId}/transactions` 하나로 둔다.
- 에디터의 표준 read 경로는 `GET /documents/{documentId}/blocks`를 사용한다.
- v1 에디터 operation은 다음 4개만 채택한다.
- `BLOCK_CREATE`
- `BLOCK_REPLACE_CONTENT`
- `BLOCK_MOVE`
- `BLOCK_DELETE`
- 초기 채택 시점에는 `BLOCK_CREATE`를 위치 전용 operation으로 두고, 본문은 같은 batch의 `BLOCK_REPLACE_CONTENT`가 맡는 쪽을 우선 채택했다.
- `BLOCK_REPLACE_CONTENT`는 range patch가 아니라 block의 structured content 전체 교체로 처리한다.
- 모든 transaction operation은 블록 참조값으로 `blockRef`를 사용한다.
- `BLOCK_CREATE`의 `blockRef`에는 새 block용 `tempId`를 넣는다.
- `blockRef`는 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`를 담는다.
- transaction 위치 참조도 `parentRef`, `afterRef`, `beforeRef`로 통일한다.
- `parentRef`, `afterRef`, `beforeRef`도 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`를 담는다.
- v1은 temp parent, temp sibling anchor를 모두 지원하고, 서버는 request 순서대로 이 ref들을 해석한다.
- `tempId`는 새 block을 같은 batch 안에서 참조하기 위한 클라이언트 로컬 식별자이며, 서버 영속 ID가 아니다.
- 서버는 새 block 생성 시 실제 `blockId`를 발급하고, 성공 응답에서 `tempId -> blockId` 매핑을 반환한다.
- 에디터 저장 queue는 클라이언트 로컬에서 관리하고, debounce 또는 명시적 flush 시점에 transaction 요청 하나로 서버에 보낸다.
- autosave는 debounce만으로 무한정 밀리면 안 되며, 장시간 연속 입력 중에도 `max autosave interval` 기준으로 강제 flush가 가능해야 한다.
- queue는 단순 FIFO가 아니라 coalescing queue로 본다.
- 생성 후 삭제처럼 의미 없는 중간 변경은 flush 전에 상쇄할 수 있다.
- queue의 coalescing, 상쇄, 최종 batch 조립은 클라이언트가 담당한다.
- 서버는 클라이언트가 정리해 보낸 최종 batch를 검증하고 반영한다.
- v1 동시성 기준은 document 전체가 아니라 block 단위 `version`이다.
- 같은 block에 대한 stale update는 `409 Conflict`로 처리한다.
- v1 transaction 실패 정책은 partial apply가 아니라 전체 rollback을 사용한다.
- conflict 응답에는 충돌 block의 최신 `version`, 최신 `content`를 포함한다.
- conflict 이후 프론트 복구 기준은 실패한 batch payload 복원이 아니라, 현재 로컬 문서 상태 기준 pending 재조립이다.
- 같은 실패 batch 안의 non-conflict 변경도 서버에는 미반영이므로, 로컬 상태가 유지되면 다시 pending에 포함될 수 있다.
- `POST /documents/{documentId}/blocks`, `PATCH /blocks/{blockId}`, `DELETE /blocks/{blockId}`는 에디터 표준 경로가 아니라 보조/운영/관리 경로로 둔다.
- 이 설계 자체는 autosave 저장 모델이며, 실시간 브로드캐스트/협업 모델을 포함하지 않는다.
- 이후 새 블록 생성 후 바로 입력하는 경로를 더 직접적으로 표현하기 위해, `BLOCK_CREATE`가 선택적 초기 `content`를 함께 받을 수 있도록 `ADR 020`에서 계약을 확장했다.

## 영향

- 장점:
- 생성, 수정, 이동, 삭제를 하나의 저장 모델로 설명할 수 있다.
- 생성 직후 삭제 같은 중간 상태를 서버에 남기지 않아도 된다.
- autosave와 `Ctrl+S`를 같은 queue flush 모델로 통합할 수 있다.
- 장시간 연속 입력 중에도 주기적 flush가 가능해, 저장이 무한정 밀리는 상태를 줄일 수 있다.
- structured content 저장을 v1 수준에서 단순한 `replace_content`로 제한할 수 있다.
- block 단위 optimistic lock으로 문서 전체 충돌보다 더 좁은 충돌 범위를 유지할 수 있다.
- transaction 전체 rollback을 사용해 프론트와 사용자가 저장 결과를 더 단순하게 이해할 수 있다.

- 단점:
- 프론트에 tempId, queue coalescing, batchId, stale 응답 무시, conflict 복구 상태가 필요하다.
- 기존 단건 block API와의 역할 경계를 문서와 구현에서 명확히 유지해야 한다.
- 같은 block 내부의 비중첩 수정도 v1에서는 block 단위 충돌로 처리된다.
- 실시간 공동 편집을 제공하려면 별도 브로드캐스트/원격 변경 적용 모델을 추가로 설계해야 한다.

## 관련 문서

- [2026-03-20-editor-save-api-boundary-and-transaction-design.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md)
- [2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
