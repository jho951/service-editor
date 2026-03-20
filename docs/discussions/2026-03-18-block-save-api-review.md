# 2026-03-18 블록 저장 API 검토 메모

## 문서 목적

- 블록 기반 문서 편집기의 저장 전략과 API 경계를 검토한다.
- 자동 저장, 수동 저장, 향후 협업 확장을 고려한 후보안을 정리한다.
- 아직 확정되지 않은 설계 논의를 저장소 문서 경로에 남긴다.

## 문서 범위

- 블록 저장 전략 비교
- 저장 API와 일반 수정 API의 책임 분리
- 현재 저장소에서 실무적으로 유리한 방향 제안

## 핵심 질문

1. autosave와 manual save를 함께 만족하는 저장 방식은 무엇인가
2. 여러 블록이 짧은 시간 안에 함께 바뀌는 흐름을 어떤 API가 가장 잘 수용하는가
3. 편집기 저장 API와 일반 PATCH API를 어디까지 분리하는 것이 적절한가

## 연결된 회의 메모

- [2026-03-18 블록 저장 API 전략 검토 메모](https://github.com/jho951/Block-server/blob/714caddbb7becd96c78a1390b37bc90f83a1545d/docs/discussions/2026-03-18-block-save-api-strategy.md)
- [2026-03-18 저장 API와 PATCH API 공존 검토 메모](https://github.com/jho951/Block-server/blob/714caddbb7becd96c78a1390b37bc90f83a1545d/docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md)

## 핵심 배경

- 현재 서비스는 문서 메타데이터와 ordered block tree를 함께 소유한다.
- 블록은 `parentId`, `sortKey`, `version`을 가진다.
- v1에서는 plain text block editor 수준을 목표로 한다.
- autosave는 요구사항 범위에 포함되어 있다.
- 실시간 협업은 현재 범위 밖이지만, 향후 확장 가능성은 열어둬야 한다.

## 선택지

### 선택지 1. 단건 PATCH 중심 저장

#### 개요

- 블록 수정, 삭제, 이동을 대부분 `PATCH /v1/blocks/{blockId}` 같은 단건 API 호출로 처리하는 방식이다.

#### 시나리오

1. 사용자가 블록 A를 수정하면 프론트가 즉시 `PATCH /v1/blocks/{blockId}`를 호출한다.
2. 이어서 블록 B 삭제, 블록 C 이동이 일어나면 각각 별도 요청을 보낸다.
3. 일부 요청만 실패하면 클라이언트가 개별 재시도와 상태 정합성을 직접 맞춰야 한다.

#### 장점

- 단순 CRUD 관점에서는 이해하기 쉽다.
- 단건 테스트와 수동 호출이 쉽다.

#### 단점

- 여러 블록이 함께 바뀌는 편집기 흐름과 맞지 않는다.
- autosave 시 요청 수가 급격히 늘어난다.
- 중간 실패 처리와 원자성 보장이 어렵다.

#### 트레이드오프

- 구현 시작은 빠를 수 있지만, 편집기 기능이 커질수록 저장 단위를 다시 도입할 가능성이 높다.

#### 적합한 상황

- 협업성이 낮고, 블록 변경량이 적고, 에디터보다는 관리형 CRUD에 가까운 경우

### 선택지 2. batch transaction 저장 + 일반 PATCH 공존

#### 개요

- 에디터 저장은 `transactions`로 묶어서 처리하고, 문서/블록 단건 수정은 일반 PATCH로 남겨두는 방식이다.

#### 시나리오

1. 사용자가 5초 동안 블록 여러 개를 수정, 삭제, 이동한다.
2. 프론트는 로컬 큐에 operation을 쌓고 autosave 시점에 `POST /v1/documents/{documentId}/transactions`로 한 번에 보낸다.
3. 문서 제목만 바꾸는 경우에는 `PATCH /v1/documents/{documentId}`를 별도로 호출한다.
4. 서버는 transaction에는 batch 단위 충돌 검증을, PATCH에는 단건 version 검증을 적용한다.

#### 장점

- 에디터 저장과 일반 수정의 책임이 분리된다.
- autosave와 여러 블록 저장 시나리오에 자연스럽다.
- 운영, 테스트, 외부 연동에서도 PATCH API를 계속 활용할 수 있다.

#### 단점

- API 종류가 늘어나 구조 설명이 더 필요하다.
- 어떤 변경을 어느 API로 보낼지 정책을 정해야 한다.

#### 트레이드오프

- 구조는 조금 더 복잡하지만 실제 사용 맥락을 더 잘 반영한다.

#### 적합한 상황

- 블록 에디터와 일반 관리성 API가 함께 존재하는 대부분의 실무 시스템

## 비교 요약

- 여러 블록 변경 저장은 단건 PATCH 연속 호출보다 batch transaction 저장이 더 자연스럽다.
- 편집기 저장과 문서 제목/아이콘 수정, 운영 보정, 외부 연동은 사용 맥락이 다르다.
- 따라서 에디터 저장용 API와 일반 PATCH API를 함께 두는 구성이 실무적으로 유리하다.

## 현재 추천 방향

### 저장 전략

- `클라이언트 로컬 반영 + debounce autosave + batch transaction 저장 + optimistic version 충돌 제어`

### API 책임 분리

- `GET /v1/documents/{documentId}/blocks`
  - 에디터 블록 초기 진입 조회
- `POST /v1/documents/{documentId}/transactions`
  - 에디터 생성/저장
- `PATCH /v1/documents/{documentId}`
  - 문서 메타데이터 일반 수정
- `POST /v1/documents/{documentId}/blocks`
  - 비에디터/관리용 새 블록 생성
- `DELETE /v1/blocks/{blockId}`
  - 명시적 subtree 삭제 보조 경로

## 추천 시나리오

1. 사용자가 문서를 열고 블록 3개를 수정하고 1개를 삭제한다.
2. 프론트는 즉시 화면만 갱신하고 pending operation을 큐에 쌓는다.
3. autosave 시점에 `POST /v1/documents/{documentId}/transactions`로 한 번에 저장한다.
4. 같은 화면에서 제목만 바꾸면 `PATCH /v1/documents/{documentId}`로 별도 처리한다.
5. 사용자가 새 블록을 만들고 입력하면 프론트는 로컬 임시 블록을 만든 뒤 `transactions` queue에 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`를 쌓는다.
6. 사용자가 블록 메뉴에서 subtree 삭제를 누르면 기본적으로 `transactions` queue에 `BLOCK_DELETE`를 쌓고, 필요 시 `DELETE /v1/blocks/{blockId}`를 보조 경로로 사용할 수 있다.

이 흐름이 사용자 편집과 운영성 요구를 가장 자연스럽게 함께 수용한다.

## 왜 이 구조를 우선 추천하는가

- autosave에서 여러 블록 변경을 한 번에 저장하기 쉽다.
- 현재 `sortKey`, `version` 모델과 자연스럽게 연결된다.
- snapshot 방식보다 협업 확장성이 좋다.
- CRDT/OT 전면 도입보다 구현 복잡도가 낮다.
- 운영, 테스트, 외부 연동에서는 일반 PATCH API가 계속 유용하다.

## 아직 확정되지 않은 쟁점

1. 블록 본문 수정은 `transactions`만 허용할지, `PATCH /v1/blocks/{blockId}`도 허용할지
2. 문서 제목 수정은 저장 큐에 포함할지, 별도 문서 PATCH로 분리할지
3. transaction 실패 시 partial apply를 허용할지, 전부 rollback할지
4. 장기적으로 revision history를 snapshot 중심으로 둘지, journal 중심으로 둘지
5. `BLOCK_CREATE`는 위치만 받을지, 일부 생성 경로에서 초기 `content`까지 허용할지

## 다음 액션 제안

1. 위 쟁점에 대한 팀 선택을 정리한다.
2. 선택이 확정되면 `docs/decisions/`에 ADR을 추가한다.
3. 채택안이 정해지면 `docs/REQUIREMENTS.md`의 API/저장 정책을 업데이트한다.
