# Editor Guideline

## 목적

이 문서는 editor operation API를 프론트와 백엔드가 함께 참고하는 공통 계약 guideline으로 정리한 문서다.

이 문서를 기준으로 다음을 한 번에 확인할 수 있어야 한다.

- 어떤 API를 `EditorOperationController` 경계로 옮겨야 하는가
- 어떤 API는 기존 리소스 controller에 남겨야 하는가
- 프론트는 어떤 endpoint와 DTO를 기준으로 호출해야 하는가
- 백엔드는 어떤 service와 mapper로 연결해야 하는가

관련 문서:

- [ADR 021](https://github.com/jho951/Block-server/blob/dev/docs/decisions/021-adopt-editor-operation-controller-boundary.md)
- [ADR 014](https://github.com/jho951/Block-server/blob/dev/docs/decisions/014-adopt-transaction-centered-editor-save-model.md)
- [editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/explainers/editor-save-model.md)
- [frontend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/frontend-editor-guideline.md)
- [backend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/backend-editor-guideline.md)

## 1. 현재 범위

현재 editor operation family는 아래 2개 endpoint를 우선 다룬다.

- `POST /editor-operations/documents/{documentId}/save`
- `POST /editor-operations/move`

이 중 문서화 깊이는 아직 save가 가장 크다.
save는 기존 저장 알고리즘을 editor save 경계 안으로 옮겨 상세 기준을 유지하고, move는 `EditorOperationController`의 단일 endpoint로 구현한 뒤 같은 문서군 안에서 점진적으로 보강한다.

## 2. 문서 구조와 확장 기준

현재 editor guide는 아래 3문서를 기본 세트로 유지한다.

- [editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/editor-guideline.md)
  - 프론트/백엔드가 같이 보는 공통 계약
- [frontend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/frontend-editor-guideline.md)
  - 프론트 구현 시점 가이드
- [backend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/backend-editor-guideline.md)
  - 백엔드 구현 시점 가이드

이렇게 두는 이유는 다음과 같다.

- save, move, 추후 restore 같은 작업은 서로 다른 operation이지만, 사용 맥락은 같은 editor 기능군 안에서 이어진다.
- operation별로 문서를 먼저 쪼개면 공통 계약과 중복 설명이 빠르게 흩어진다.
- 그래서 현재는 editor 기능군 아래에서 공통 계약 1개와 역할별 가이드 2개를 같이 유지하는 편이 더 읽기 쉽다.

운영 기준은 아래를 따른다.

- 공통 endpoint, DTO, 상태 전이, 실패 계약은 `editor-guideline.md`에 먼저 정리한다.
- 프론트 구현 세부는 `frontend-editor-guideline.md`, 백엔드 구현 세부는 `backend-editor-guideline.md`에 나눈다.
- save, move, restore처럼 editor 안의 operation이 늘어나도 우선 이 3문서를 확장한다.
- 특정 operation이 너무 커져서 읽기 어려워지면, 그때 `save.md`, `move.md`, `restore.md` 같은 하위 문서 분리를 검토한다.
- 새 하위 문서를 만들더라도 공통 계약은 `editor-guideline.md`에 남기고, 세부 해설만 분리한다.

## 3. 이름 기준

컨트롤러 이름은 `EditorController`보다 `EditorOperationController`를 사용한다.

이유는 다음과 같다.

- `EditorController`는 에디터 화면 전체를 대표하는 이름처럼 읽혀 범위가 너무 넓다.
- 현재 경계는 조회, 문서 메타데이터, 휴지통, 복구 전체가 아니라 editor interaction write operation에 한정된다.
- `EditorOperationController`는 "에디터가 발생시키는 작업을 받는 경계"라는 뜻이 바로 드러난다.

즉 지금 기준에서는 `EditorOperationController`가 가장 무난하고 명확하다.

## 4. 먼저 고정할 경계

`EditorOperationController`는 generic controller가 아니다.

이 controller는 아래처럼 "에디터 상호작용에서 발생하는 write operation"만 받는 별도 입구다.

- 문서 이동
- 블록 이동
- editor save batch 반영

반대로 아래는 여기에 두지 않는다.

- 문서 생성 / 조회 / 메타데이터 수정 / 공개 상태 수정 / 휴지통 / 복구
- 블록 생성 / 내용 수정 / 삭제의 리소스 CRUD 성격 API
- 운영자 전용 단건 보정 API 전체

즉 기준은 단순하다.

- 리소스 상태 CRUD면 기존 리소스 controller
- editor interaction write면 `EditorOperationController`

## 5. 공통 endpoint 기준

v1 구현 시작점은 아래 2개로 고정한다.

### 1. 문서 save

`POST /editor-operations/documents/{documentId}/save`

- 역할:
- document context 안의 editor save batch 반영
- editor save model의 외부 진입점

- 프론트 기준:
- autosave / 명시적 save / leave flush가 모두 이 endpoint로 간다.

- 백엔드 기준:
- `EditorSaveApiMapper`
- `EditorOperationOrchestrator.save(...)`

### 2. move

`POST /editor-operations/move`

- 역할:
- 문서 이동과 블록 이동을 하나의 명시적 move contract로 처리
- drag 중간 상태가 아니라 drop 확정 시점의 최종 위치만 반영

- 프론트 기준:
- 문서 트리 drag and drop과 블록 drag and drop은 모두 이 endpoint를 사용한다.
- 요청에서 `resourceType`으로 이동 대상을 구분한다.
- drag 중간 hover 변화마다 호출하지 않고, drop 확정 시점에만 1회 호출한다.

- 백엔드 기준:
- `resourceType=DOCUMENT`면 기존 `DocumentService.move(...)`로 연결한다.
- `resourceType=BLOCK`면 기존 `BlockService.move(...)`로 연결한다.
- controller는 move contract를 받고 `resourceType`에 따라 validation과 service 연결을 분기한다.
- no-op drop이면 성공으로 처리할 수 있지만 실제 갱신과 버전 증가는 생기지 않게 한다.

## 6. 하지 않을 구조

아래 구조는 채택하지 않는다.

### 범용 operation dispatcher

```text
POST /editor-operations
```

여기서 `operationType`으로 모든 작업을 분기하는 방식은 채택하지 않는다.

이 방식은 다음 문제를 만든다.

- request DTO에 nullable 필드가 늘어난다.
- validation이 controller나 dispatcher 분기문에 몰린다.
- 작업 의미가 endpoint 자체에서 사라진다.

주의:

- `POST /editor-operations/move`는 허용한다.
- 다만 이것은 move 하나만 공통화한 명시적 endpoint다.
- 모든 작업을 한 endpoint에서 받는 범용 dispatcher와는 다르다.

### 의미 없는 facade service

```text
EditorOperationController
  -> EditorOperationFacade
    -> switch(type)
      -> DocumentService / BlockService / EditorOperationOrchestrator
```

단순 라우팅만 하는 facade 계층은 만들지 않는다.

다만 editor 공통 API 경계를 조율하는 `EditorOperationOrchestrator` 자체는 둔다.
문제는 "공통 orchestrator"가 아니라, 의미 없이 전달만 하는 facade다.

## 7. 역할 분리

### 프론트가 이 문서에서 가져가야 하는 것

- 어떤 편집 이벤트가 어떤 endpoint로 가는지
- save와 move를 서로 다른 operation으로 본다는 점
- save와 move의 request DTO를 합치지 않는다는 점
- 실패 시 어느 서비스가 어떤 정책을 가진 endpoint인지

### 백엔드가 이 문서에서 가져가야 하는 것

- controller 경계 아래에는 `EditorOperationOrchestrator` 하나를 두고, save와 move를 같은 editor operation family로 읽는다는 점
- save는 public editor 진입점과 실행 구조 모두 `EditorSave*` 기준으로 정리하고, 기존 save 알고리즘만 유지한다는 점
- move는 `EditorMove*` 기준으로 orchestrator에 편입하되, 기존 문서/블록 이동 알고리즘은 그대로 재사용한다는 점

### `EditorOperationController`

- operation endpoint를 노출한다.
- path variable과 request body를 받아 save와 move를 모두 `EditorOperationOrchestrator` 호출로 연결한다.
- 공통 응답 포맷, 인증 사용자 식별, mapper 호출만 담당한다.
- 도메인 정책 분기 허브가 되면 안 된다.

### request / response DTO

- endpoint 의미가 다른 만큼 DTO도 분리한다.
- save와 move를 한 request 타입으로 합치지 않는다.
- move는 문서와 블록을 함께 받는 단일 request 타입을 사용한다.
- 다만 `resourceType`에 따라 허용 필드와 validation 규칙은 분기한다.

### mapper

- save는 `EditorSaveApiMapper`를 기준으로 받고, command/result/operation type도 `EditorSave*` family로 맞춘다.
- move는 `EditorMoveApiMapper`, `EditorMoveCommand`, core `EditorMoveResourceType` 기준으로 같은 family 안에서 정리한다.

### service

- editor 공통 orchestrator는 `EditorOperationOrchestrator` 하나로 둔다.
- save는 `EditorOperationOrchestrator.save(...)`가 editor 경계의 진입점이 되고, 내부 실행도 `EditorSaveOperationExecutor`, `EditorSaveContext` 같은 editor save 구조로 수행한다.
- move는 `EditorOperationOrchestrator.move(...)`가 editor 경계의 진입점이 되고, 문서 이동은 `DocumentService.move(...)`, 블록 이동은 editor save의 `BLOCK_MOVE` 실행 경로를 재사용한다.
- orchestrator는 editor 유스케이스 조립 계층이지, 모든 도메인 로직을 직접 구현하는 계층이 아니다.

## 8. DTO 기준

save DTO는 `EditorSaveRequest`, `EditorSaveResponse` 기준으로 정리한다.
move DTO는 현재 `EditorMoveOperationRequest`와 core `EditorMoveResourceType` 기준으로 구현한다.

### save request / response

save는 외부 API 이름뿐 아니라 public command/response/type도 `EditorSave*` family로 정리한다.

```java
public class EditorSaveRequest {
    private String clientId;
    private String batchId;
    private List<EditorSaveOperationRequest> operations;
}
```

```java
public class EditorSaveOperationRequest {
    private String opId;
    private EditorSaveOperationType type;
    private String blockRef;
    private Long version;
    private String parentRef;
    private String afterRef;
    private String beforeRef;
    private Object content;
}
```

```java
public class EditorSaveResponse {
    private Long documentVersion;
    private List<EditorSaveAppliedOperationResponse> appliedOperations;
}
```

현재 save의 public DTO는 `EditorSaveRequest`, `EditorSaveResponse`를 사용한다.
내부 실행 구조도 `EditorSaveOperationType`, `EditorSaveCommand`, `EditorSaveResult`, `EditorSaveOperationExecutor` 기준으로 맞춘다.

### move request

```java
public class EditorMoveOperationRequest {
    private EditorMoveResourceType resourceType;
    private UUID resourceId;
    private UUID targetParentId;
    private UUID afterId;
    private UUID beforeId;
    private Long version;
}
```

```java
public enum EditorMoveResourceType {
    DOCUMENT,
    BLOCK
}
```

```java
public class EditorMoveResponse {
    private EditorMoveResourceType resourceType;
    private UUID resourceId;
    private UUID parentId;
    private Long version;
    private Long documentVersion;
    private String sortKey;
}
```

주의:

- `resourceType=DOCUMENT`면 `targetParentId`, `afterId`, `beforeId`를 문서 ID 기준으로 해석한다.
- `resourceType=BLOCK`면 같은 필드를 블록 ID 기준으로 해석한다.
- `version`은 block move에서는 필수, document move에서는 선택 또는 미사용으로 둘 수 있다.
- 이 request는 move 하나만 공통화한 contract다. create/update/delete까지 같은 방식으로 확장하지 않는다.

현재 move는 `EditorMoveOperationRequest`가 core `EditorMoveResourceType`를 직접 사용하고, `EditorMoveApiMapper`, `EditorMoveCommand`를 거쳐 `EditorOperationOrchestrator.move(...)`로 연결한다.
문서 이동은 기존 `DocumentService.move(...)`, 블록 이동은 editor save의 `BLOCK_MOVE` 실행 경로를 재사용한다.
응답은 `EditorMoveResponse`로 돌려주고, 프론트가 후속 상태 동기화에 필요한 `resourceId`, `parentId`, `version`, `documentVersion`, `sortKey`를 포함한다.

## 9. 구현 순서

1. `EditorOperationController`를 추가한다.
2. document save endpoint를 먼저 옮기고 기존 저장 알고리즘이 editor save 구조 안에서 유지되는지 확인한다.
3. 단일 move endpoint를 추가하고 문서/블록 이동 요청을 모두 이 경계로 옮긴다.
4. `resourceType` 기준 validation과 service 연결을 정리한다.
5. Swagger 태그와 summary를 operation 성격에 맞게 정리한다.
6. 기존 리소스 controller에서 빠진 endpoint의 책임 설명을 문서와 코드 주석 없이 구조 자체로 드러낸다.

## 10. 검증 체크리스트

- `DocumentController`에는 문서 메타데이터 / 조회 / 휴지통 / 복구만 남는가
- `EditorOperationController`가 범용 `type` 분기 endpoint로 무너지지 않는가
- save endpoint가 기존 저장 알고리즘을 editor save 구조 안에서 그대로 유지하는가
- move endpoint가 단일 contract를 쓰더라도 `resourceType`별 검증과 service 연결이 분명한가
- 프론트와 백엔드 guide가 이 guideline을 공통 계약 문서로 참조하고 있는가
- admin block 보조 API와 editor operation 표준 API의 역할이 문서상 분명한가
