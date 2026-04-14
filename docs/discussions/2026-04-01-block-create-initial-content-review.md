# 2026-04-01 block create initial content 허용 검토 메모

## 문서 목적

- editor transaction에서 `BLOCK_CREATE`가 초기 `content`를 함께 받을 수 있게 할지 검토한다.
- 새 블록 생성 후 바로 입력하는 가장 흔한 저장 경로를 현재 모델보다 더 자연스럽게 표현할 수 있는지 본다.
- 이 문서는 채택 전 검토 메모이며, 채택 시 ADR과 `docs/REQUIREMENTS.md`까지 함께 갱신한다.

## 배경

- 현재 editor 저장 표준 경로는 `POST /documents/{documentId}/transactions`다.
- 현재 v1 operation은 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE` 4개다.
- 현행 계약은 `BLOCK_CREATE`가 위치만 다루고, 새 블록 본문은 같은 batch의 `BLOCK_REPLACE_CONTENT`가 담당한다.
- 하지만 실제 편집 흐름에서는 새 블록을 만든 뒤 바로 내용을 입력하고, debounce flush 시점에는 이미 최종 `content`가 로컬 state에 올라와 있는 경우가 많다.
- 이 경우 같은 temp block에 대해 `create + replace_content`를 항상 별도 operation으로 유지해야 하는지 다시 볼 필요가 있다.

## 검토 범위

- `BLOCK_CREATE`가 선택적 `content`를 받는 계약
- create와 replace_content 역할 경계
- frontend queue coalescing 기준
- backend validation과 tempId 해석 영향

## 핵심 질문

1. 새 블록 생성 후 바로 입력한 경우, `BLOCK_CREATE`가 초기 `content`를 함께 저장해도 되는가
2. 이 변경이 create와 replace_content의 책임 경계를 과도하게 흐리는가
3. temp block에 대한 `BLOCK_CREATE + BLOCK_REPLACE_CONTENT`를 어떤 계층에서 접는 것이 적절한가

## 고려한 자료와 사례

- [2026-03-20-editor-save-api-boundary-and-transaction-design.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md)
- [2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md)
- [014-adopt-transaction-centered-editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/014-adopt-transaction-centered-editor-save-model.md)
- [editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/explainers/editor-save-model.md)
- [editor/frontend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/frontend-editor-guideline.md)

## 선택지

### 선택지 1. create와 replace_content를 항상 분리 유지

#### 개요

- `BLOCK_CREATE`는 위치만 확정한다.
- 새 블록의 본문은 항상 같은 batch의 `BLOCK_REPLACE_CONTENT`가 맡는다.

#### 시나리오

1. 사용자가 Enter로 새 블록을 만든다.
2. 프론트는 `tempId`를 만들고 queue에 `BLOCK_CREATE(tempId)`를 넣는다.
3. 사용자가 바로 입력하면 queue에 `BLOCK_REPLACE_CONTENT(tempId, latestContent)`를 넣는다.
4. flush 시 서버는 create 후 replace_content를 순서대로 적용한다.

#### 장점

- create와 본문 수정의 operation 의미가 가장 선명하다.
- 새 블록 생성과 기존 블록 본문 수정이 같은 `BLOCK_REPLACE_CONTENT` 경로를 공유한다.

#### 단점

- 새 블록 생성 후 바로 입력하는 가장 흔한 경로에서도 operation과 DB write가 하나 더 필요하다.
- 프론트는 temp block의 create와 replace를 늘 쌍으로 관리해야 한다.

#### 트레이드오프

- 의미는 가장 분명하지만, autosave batch 모델의 최종 상태 정리 효과를 덜 활용한다.

#### 적합한 상황

- 사용자 편집 이벤트와 서버 operation을 최대한 1:1로 맞추고 싶을 때

### 선택지 2. `BLOCK_CREATE`가 선택적 initial content를 함께 받는다

#### 개요

- `BLOCK_CREATE`는 위치를 항상 다루고, 필요하면 초기 `content`도 함께 받는다.
- `content`가 없으면 서버는 기존과 같이 empty structured content를 저장한다.
- 기존 block의 후속 본문 변경은 계속 `BLOCK_REPLACE_CONTENT`가 담당한다.

#### 시나리오

1. 사용자가 Enter로 새 블록을 만든다.
2. 프론트는 `tempId`를 만들고 로컬 block tree에 빈 블록을 먼저 넣는다.
3. 사용자가 debounce 전에 바로 입력하면 queue는 `BLOCK_CREATE(tempId, content=latestContent)` 하나만 남길 수 있다.
4. 사용자가 내용을 입력하지 않은 채 flush되면 `BLOCK_CREATE(tempId)`만 보낸다.
5. 사용자가 flush 뒤에 다시 수정하면 그때는 실제 `blockId` 기준 `BLOCK_REPLACE_CONTENT`가 담당한다.

#### 장점

- 새 블록 생성 후 바로 입력하는 흔한 경로를 더 짧게 표현할 수 있다.
- 프론트 queue가 temp block의 `create + replace_content`를 하나로 접을 수 있다.
- 서버도 새 블록의 초기 상태를 create 단계에서 바로 materialize할 수 있다.

#### 단점

- `BLOCK_CREATE`가 위치만이 아니라 초기 본문까지 맡을 수 있어 의미가 조금 넓어진다.
- 문서와 검증 규칙을 함께 갱신해야 한다.

#### 트레이드오프

- operation 의미 일부를 넓히는 대신, autosave batch의 "최종 상태 저장" 특성을 더 잘 살린다.

#### 적합한 상황

- 새 블록 생성 후 곧바로 입력하는 경로가 흔하고, flush 전 coalescing을 적극 활용하는 editor

## 비교 요약

- 선택지 1은 operation 의미가 가장 선명하지만, temp block에 대한 `create + replace_content`를 항상 유지해야 한다.
- 선택지 2는 create 의미를 약간 넓히지만, 새 블록의 초기 상태를 더 직접적으로 표현할 수 있다.
- autosave와 coalescing이 핵심인 현재 editor 저장 모델에는 선택지 2가 더 실무적이다.

## 추천 시나리오

- 사용자가 Enter로 새 블록을 만든 뒤 바로 입력한다.
- 프론트는 로컬 UI를 즉시 갱신하고, flush 전 queue를 정리해 `BLOCK_CREATE(content=latestContent)` 하나로 보낸다.
- 사용자가 입력하지 않은 채 넘어가면 `content` 없는 `BLOCK_CREATE`만 보낸다.
- 사용자가 flush 후에 다시 수정하면 그때부터는 일반 `BLOCK_REPLACE_CONTENT`로 이어간다.

이 흐름이면 "블록 생성"과 "후속 본문 수정"의 개념적 경계는 유지하면서도, 새 블록의 초기 상태를 더 간결하게 저장할 수 있다.

## 현재 추천 방향

- `BLOCK_CREATE`는 위치 필드와 함께 선택적 `content`를 받을 수 있게 한다.
- `content`가 없으면 서버는 현재와 같은 empty structured content fallback을 사용한다.
- 새 temp block에 대한 `BLOCK_CREATE + BLOCK_REPLACE_CONTENT`는 프론트 queue가 flush 전에 `BLOCK_CREATE(content=latestContent)`로 접는 것을 우선 권장한다.
- 다만 서버는 temp block을 참조하는 `BLOCK_REPLACE_CONTENT`도 계속 허용해, 프론트 coalescing 미적용 케이스를 방어적으로 수용한다.
- 기존 서버 block의 본문 변경은 계속 `BLOCK_REPLACE_CONTENT`가 담당한다.

## 미해결 쟁점

1. `BLOCK_CREATE`의 필드명을 그대로 `content`로 둘지, `initialContent`로 분리할지
2. 프론트가 coalescing을 반드시 보장할지, 서버가 `create + replace_content` 중복을 추가 최적화할지
3. admin 보조 API와 editor 표준 API 사이의 설명 문구를 어디까지 맞출지

## 다음 액션

1. 이 방향을 채택하면 ADR과 `docs/REQUIREMENTS.md`를 갱신한다.
2. transaction DTO, frontend guide, backend guide, explainer를 새 계약 기준으로 보강한다.
3. 이후 구현 단계에서 request validation, executor, 통합 테스트를 함께 조정한다.

## 관련 문서

- [2026-03-20-editor-save-api-boundary-and-transaction-design.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-save-api-boundary-and-transaction-design.md)
- [2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md)
- [014-adopt-transaction-centered-editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/014-adopt-transaction-centered-editor-save-model.md)
- [020-allow-optional-content-on-block-create-in-transactions.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/020-allow-optional-content-on-block-create-in-transactions.md)
- [2026-03-20-editor-transaction-save-model.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-20-editor-transaction-save-model.md)
