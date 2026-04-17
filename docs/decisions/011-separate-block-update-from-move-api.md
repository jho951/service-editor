# ADR 011: 블록 수정 API와 이동 API 분리

## 상태

채택됨

## 배경

블록 편집에서 다음 두 동작은 성격이 다르다.

- 블록 수정: 블록 자신의 내용이나 메타데이터 변경
- 블록 이동: 부모 변경과 순서 변경을 포함한 구조 변경

기존 요구사항에서는 `PATCH /blocks/{blockId}`가 내용 수정과 이동을 함께 담당하도록 되어 있었다. 하지만 이 방식은 다음 문제를 만든다.

- 수정과 이동의 검증 규칙이 다르다.
- 이동에서만 `sortKey` 재계산이 필요하다.
- drag and drop 기반 이동은 drop 시점의 단일 command로 보는 편이 자연스럽다.
- 수정 API에 구조 변경 책임까지 넣으면 구현과 테스트가 복잡해진다.

## 결정

- 블록 내용 수정과 블록 이동은 같은 API 책임으로 섞지 않는다.
- 현재 보조 경로 기준 내용 수정은 `PATCH /admin/blocks/{blockId}`, 단일 이동은 `POST /admin/blocks/{blockId}/move`에서 처리한다.
- 에디터 표준 이동은 후속 결정에 따라 `POST /editor-operations/move`로 처리한다.
- 단건 admin move는 `parentRef`, `afterRef`, `beforeRef`, `version`으로 대상 위치를 해석하고, editor move는 `targetParentId`, `afterId`, `beforeId`, `version`으로 대상 위치를 해석한다.
- 이동 처리에서는 기존 `sortKey` 정책을 사용해 새 위치의 `sortKey`를 계산한다.
- v1에서는 단일 블록 이동 API를 우선 채택하고, 별도 다중 블록 reorder API는 기본 경로로 두지 않는다.

## 영향

- 장점:
- 블록 수정 API가 단순해진다.
- 블록 이동의 구조적 책임과 정렬 책임이 분리된다.
- drag and drop UI와 API 의미가 자연스럽게 맞는다.
- 단건 수정과 단건 이동 테스트가 쉬워진다.

- 단점:
- 블록 관련 API 수가 늘어난다.
- 여러 블록 일괄 reorder가 필요해지면 후속 설계가 추가로 필요하다.
