# ADR 021: 에디터 성격의 write API는 EditorOperationController 경계로 모은다

## 상태

채택됨

## 배경

`Document`와 `Block`은 영속 모델과 도메인 정책이 다르다.

- `Document`는 제목, 공개 상태, 휴지통, 소유 검증이 중심이다.
- `Block`은 문서 내부 content, block type, parent block 정합성이 중심이다.

그래서 `Node` 단일 영속 엔티티나 `nodes` 테이블로 통합하는 방식은 현재 구조에서 과하다.

반면 에디터 상호작용에서 발생하는 write는 공통 operation 성격이 더 강하다.

- 문서 이동
- 블록 이동
- 문서 컨텍스트 안의 editor save batch

이 작업들은 리소스 CRUD보다 "구조 변경" 또는 "편집 operation 반영"이라는 같은 축에서 읽히는 편이 자연스럽다.

기존 `DocumentController`에 문서 이동과 save가 함께 들어가 있으면 문서 메타데이터 CRUD와 editor operation 경계가 흐려진다.
또한 `Document`와 `Block` 전체를 generic controller 하나로 받는 구조는 `type` 분기 facade만 늘리고 의미 있는 공통화 이점을 만들지 못한다.

## 결정

- `Node` 영속 엔티티와 `nodes` 단일 테이블은 도입하지 않는다.
- 리소스 CRUD와 editor operation write는 다른 축으로 나눈다.
- 에디터 성격의 write API는 `EditorOperationController` 경계에 둔다.
- `EditorOperationController`는 하나의 범용 endpoint가 아니라, 의미가 분명한 operation별 endpoint 묶음으로 구성한다.
- 이름은 `EditorController`처럼 넓게 잡지 않고, editor interaction write만 다룬다는 의미가 드러나도록 `EditorOperationController`를 사용한다.
- v1 기준 우선 반영 대상은 다음 2개다.
- `POST /editor-operations/documents/{documentId}/save`
- `POST /editor-operations/move`
- `EditorOperationController` 아래에는 editor 공통 application 계층인 `EditorOperationOrchestrator`를 둔다.
- save endpoint는 editor 통합 구조 안에서 `EditorOperationOrchestrator.save(...)`로 받고, save 실행 구조도 `EditorSave*` 계열로 editor 문맥 안에 둔다.
- 기존 save 로직의 실행 알고리즘은 유지하되, editor 경계에서는 `DocumentTransaction*`가 아니라 `EditorSave*` 이름과 구조를 중심으로 사용한다.
- move는 문서 이동과 블록 이동을 하나의 명시적 move endpoint로 통합한다.
- move request는 `resourceType`, `resourceId`, `targetParentId`, `afterId`, `beforeId`, `version` 기준으로 설계한다.
- move는 `EditorOperationOrchestrator.move(...)`로 받고, 문서 이동은 기존 `DocumentService.move(...)`, 블록 이동은 editor save의 `BLOCK_MOVE` 실행 경로를 재사용한다.
- 이 분기는 모든 operation을 받는 범용 dispatcher가 아니라, move 하나만 공통화한 단일 operation contract 안에서만 허용한다.
- move API는 drag 중간 상태를 저장하지 않고, drop 확정 시점의 최종 위치만 1회 반영하는 explicit structure action으로 사용한다.
- create/read/update/delete/trash/restore 같은 리소스 CRUD는 기존 `DocumentController`, block 전용 controller 경계에 남긴다.

## 영향

- 장점:
- 리소스 CRUD와 editor operation write 경계가 분명해진다.
- `Document`와 `Block`의 공통화를 영속 모델이 아니라 operation 경계에서만 가져갈 수 있다.
- save와 move를 같은 editor orchestrator 경계에서 읽되, 기존 저장/이동 알고리즘은 그대로 재사용할 수 있다.
- move API를 호출하는 쪽에서는 대상 종류만 바꿔 같은 contract를 재사용할 수 있다.

- 단점:
- 기존 `DocumentController`의 save / move path와 책임을 재배치해야 한다.
- move 편입 이후에도 `resourceType`별 validation과 service 연결 규칙을 명확히 관리해야 한다.
- 기존 admin block 보조 API와 새 operation path의 역할을 문서와 구현에서 명확히 구분해야 한다.

## 관련 문서

- [2026-04-05-node-domain-abstraction-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-04-05-node-domain-abstraction-review.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [editor/editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/editor-guideline.md)
