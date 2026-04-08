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
- [editor-transaction-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/explainers/editor-transaction-save-model.md)
- [frontend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/frontend-editor-guideline.md)
- [backend-editor-guideline.md](https://github.com/jho951/Block-server/blob/dev/docs/guides/editor/backend-editor-guideline.md)

## 1. 현재 범위

현재 editor operation family는 아래 2개 endpoint를 우선 다룬다.

- `POST /editor-operations/documents/{documentId}/save`
- `POST /editor-operations/move`

이 중 문서화 깊이는 아직 save가 가장 크다.
save는 기존 transaction 기반 설명을 이 기능군 안으로 편입해 상세 기준을 유지하고, move는 `EditorOperationController`의 단일 endpoint로 구현한 뒤 같은 문서군 안에서 점진적으로 보강한다.

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
- 기존 transaction-centered save model의 외부 진입점

- 프론트 기준:
- autosave / 명시적 save / leave flush가 모두 이 endpoint로 간다.

- 백엔드 기준:
- `DocumentTransactionApiMapper`
- `DocumentTransactionService`

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
- `resourceType=DOCUMENT`면 `DocumentService.move(...)`로 연결한다.
- `resourceType=BLOCK`면 block 이동 정책을 가진 서비스로 연결한다.
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
      -> DocumentService / BlockService / DocumentTransactionService
```

단순 라우팅만 하는 facade 계층은 만들지 않는다.

endpoint가 이미 분리되어 있으면 controller가 각 서비스로 직접 연결해도 충분하다.

## 7. 역할 분리

### 프론트가 이 문서에서 가져가야 하는 것

- 어떤 편집 이벤트가 어떤 endpoint로 가는지
- save와 move를 서로 다른 operation으로 본다는 점
- save와 move의 request DTO를 합치지 않는다는 점
- 실패 시 어느 서비스가 어떤 정책을 가진 endpoint인지

### 백엔드가 이 문서에서 가져가야 하는 것

- controller 경계는 공통 operation 축이지만, 도메인 service는 계속 분리한다는 점
- save는 기존 transaction orchestration을 그대로 재사용한다는 점
- move는 단일 endpoint로 받되 `resourceType`에 따라 validation과 service 연결을 분기한다는 점

### `EditorOperationController`

- operation endpoint를 노출한다.
- path variable과 request body를 받아 application service 호출로 연결한다.
- 공통 응답 포맷, 인증 사용자 식별, mapper 호출만 담당한다.
- 도메인 정책 분기 허브가 되면 안 된다.

### request / response DTO

- endpoint 의미가 다른 만큼 DTO도 분리한다.
- save와 move를 한 request 타입으로 합치지 않는다.
- move는 문서와 블록을 함께 받는 단일 request 타입을 사용한다.
- 다만 `resourceType`에 따라 허용 필드와 validation 규칙은 분기한다.

### mapper

- save는 기존 `DocumentTransactionApiMapper`를 우선 재사용한다.
- move 계열도 필요하면 endpoint 전용 mapper를 둘 수 있지만, 단순 필드 전달이면 mapper를 억지로 만들지 않는다.

### service

- save는 기존 `DocumentTransactionService`가 계속 중심이다.
- move는 공통 endpoint로 받되, 문서 이동은 `DocumentService.move(...)`, 블록 이동은 block 이동 정책 서비스가 담당한다.
- controller 경계가 생긴다고 해서 공통 `EditorOperationService`를 반드시 만들 필요는 없다.

## 8. DTO 기준

save DTO는 외부 계약 분리 시점까지 기존 transaction request/response 재사용을 허용한다.
move DTO는 현재 `EditorMoveOperationRequest`, `EditorMoveResourceType` 기준으로 구현한다.

### save request / response

save는 외부 API 이름을 `save`로 드러내되, 내부 transaction 모델은 그대로 유지한다.

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

현재 구현도 외부 endpoint만 `save`로 옮기고, 내부 DTO와 mapper는 기존 `DocumentTransactionRequest`, `DocumentTransactionResponse`를 그대로 재사용한다.

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

주의:

- `resourceType=DOCUMENT`면 `targetParentId`, `afterId`, `beforeId`를 문서 ID 기준으로 해석한다.
- `resourceType=BLOCK`면 같은 필드를 블록 ID 기준으로 해석한다.
- `version`은 block move에서는 필수, document move에서는 선택 또는 미사용으로 둘 수 있다.
- 이 request는 move 하나만 공통화한 contract다. create/update/delete까지 같은 방식으로 확장하지 않는다.

## 9. 구현 순서

1. `EditorOperationController`를 추가한다.
2. document save endpoint를 먼저 옮기고 기존 transaction 서비스 재사용을 확인한다.
3. 단일 move endpoint를 추가하고 문서/블록 이동 요청을 모두 이 경계로 옮긴다.
4. `resourceType` 기준 validation과 service 연결을 정리한다.
5. Swagger 태그와 summary를 operation 성격에 맞게 정리한다.
6. 기존 리소스 controller에서 빠진 endpoint의 책임 설명을 문서와 코드 주석 없이 구조 자체로 드러낸다.

## 10. 검증 체크리스트

- `DocumentController`에는 문서 메타데이터 / 조회 / 휴지통 / 복구만 남는가
- `EditorOperationController`가 범용 `type` 분기 endpoint로 무너지지 않는가
- save endpoint가 기존 transaction orchestration을 그대로 재사용하는가
- move endpoint가 단일 contract를 쓰더라도 `resourceType`별 검증과 service 연결이 분명한가
- 프론트와 백엔드 guide가 이 guideline을 공통 계약 문서로 참조하고 있는가
- admin block 보조 API와 editor operation 표준 API의 역할이 문서상 분명한가
