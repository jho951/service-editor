# Document Resource Boundary And Transaction Flow

## 목적

이 문서는 현재 editor-service가 문서/블록 본문, editor operation, 외부 리소스, 플랫폼 runtime을 어떻게 나눠서 처리하는지 한 번에 설명하기 위한 문서다.

대상 범위는 다음과 같다.

- `documents`, `blocks`, `blocks.content_json`의 canonical source 기준
- `EditorOperationController`와 `EditorOperationOrchestrator`의 현재 write 경계
- 첨부파일과 문서 스냅샷의 `platform-resource` 처리 흐름
- `document_resources`, outbox relay, governance bridge의 연결 방식
- binding 상태 모델과 purge/reconcile 운영 방식
- 현재 트랜잭션 경계와 운영 교체 전제

관련 문서:

- [REQUIREMENTS.md](https://github.com/jho951/editor-service/blob/dev/docs/REQUIREMENTS.md)
- [ADR 021](https://github.com/jho951/editor-service/blob/dev/docs/decisions/021-adopt-editor-operation-controller-boundary.md)
- [ADR 022](https://github.com/jho951/editor-service/blob/dev/docs/decisions/022-keep-document-canonical-state-in-db-and-link-external-resources.md)
- [editor-save-model.md](https://github.com/jho951/editor-service/blob/dev/docs/explainers/editor-save-model.md)

## 1. 현재 소유권 경계

현재 기준은 다음 한 문장으로 정리할 수 있다.

> 문서 자체 상태는 editor-service DB가 소유하고, 문서가 참조하거나 생성하는 외부 리소스는 `platform-resource`가 소유한다.

구체적으로는 아래와 같다.

- editor-service DB canonical
  - `documents`
  - `blocks`
  - `blocks.content_json`
  - 문서/블록 version 규칙
  - editor save, move, delete, restore 같은 편집 도메인 규칙
- `platform-resource` canonical
  - 블록 첨부파일
  - 문서 스냅샷
  - 향후 에셋, export 결과물, import 원본, backup artifact
  - `owner`, `kind`, `catalog`, `access`, `lifecycle`, `event`

중요한 점은 `blocks.content_json`을 아직 `platform-resource`로 옮기지 않았다는 것이다.
이 값은 파일 저장 위치가 아니라 편집기의 canonical source 문제와 연결되므로, 현재 구현은 DB row canonical 모델을 유지한다.

## 2. 에디터 write 경계

에디터 협업 성격의 write는 `EditorOperationController` 아래에 모인다.

- `POST /editor-operations/documents/{documentId}/save`
- `POST /editor-operations/move`

반대로 문서 생성/조회/메타데이터 수정/휴지통/복구 같은 리소스 CRUD는 기존 문서 controller에 남는다.

### save 흐름

현재 save 흐름은 아래 순서다.

1. `EditorOperationController.save(...)`
2. `EditorSaveApiMapper`
3. `EditorOperationOrchestrator.save(...)`
4. `DocumentAccessGuard.requireWritable(...)`
5. `EditorSaveOperationExecutor`가 batch operation 적용
6. 실제 변경이 하나라도 있으면 `DocumentVersionUpdater.increment(...)`
7. `EditorSaveResult` 응답 조립

여기서 중요한 정책은 두 가지다.

- save는 문서 쓰기 guard를 먼저 통과해야 한다.
- document version은 모든 operation마다 올리지 않고, 실제 반영된 operation이 있을 때만 마지막에 별도 정책으로 증가시킨다.

### move 흐름

현재 move는 문서 이동과 블록 이동을 같은 controller 경계에서 받되, 내부 처리 경로는 다르다.

- document move
  1. `EditorOperationController.move(...)`
  2. `EditorOperationOrchestrator.move(...)`
  3. `DocumentAccessGuard.requireWritable(...)`
  4. `DocumentService.move(...)`
  5. `PersistenceContextManager.flush()`
  6. 응답 조립
- block move
  1. `EditorOperationController.move(...)`
  2. `EditorOperationOrchestrator.move(...)`
  3. `BlockAccessGuard.requireWritable(...)`
  4. `EditorSaveOperationExecutor.applyMove(...)`
  5. 기존 `BlockService.move(...)` 경로 재사용
  6. 문서 재조회 후 응답 조립

즉 move는 공통 endpoint를 쓰지만, 문서와 블록의 도메인 알고리즘을 하나로 합치지 않는다.

## 3. 외부 리소스 처리 흐름

현재 외부 리소스로 구현된 것은 두 종류다.

- `editor-attachment`
- `document-snapshot`

둘 다 `ResourceService` 공개 계약만 사용하고, 서비스 코드가 storage primitive나 lifecycle wiring을 직접 조립하지 않는다.

### 블록 첨부파일 저장

현재 블록 첨부파일 저장 흐름은 다음과 같다.

1. 요청 진입
2. `BlockAccessGuard.requireWritable(...)`
3. 문서 owner를 `ResourceOwner`로 결정
4. 현재 요청자를 `ResourcePrincipal`로 생성
5. `ResourceService.store(...)`
6. `document_resources`에 binding row 저장
7. `platform-resource` outbox에 lifecycle event 적재
8. relay가 publisher와 governance bridge로 전달

조회와 다운로드는 아래 순서를 따른다.

1. block 읽기 guard 확인
2. `document_resources` 활성 binding 확인
3. `resourceService.describe(...)` 또는 `resourceService.open(...)`
4. descriptor attribute의 `blockId`, `documentId` 재검증

삭제도 같은 방식으로 guard와 binding 검증을 먼저 거친 뒤 resource delete와 binding soft delete를 수행한다.

### 문서 스냅샷 저장

문서 스냅샷도 구조는 비슷하지만, 본문을 외부화하는 것이 아니라 현재 DB canonical 상태를 파생 artifact로 직렬화한다.

1. `DocumentAccessGuard.requireWritable(...)`
2. 문서와 활성 블록 목록 조회
3. JSON snapshot 직렬화
4. `ResourceService.store(...)` with `document-snapshot`
5. `document_resources`에 snapshot binding 저장
6. lifecycle event를 outbox에 적재
7. relay가 후속 전달

즉 스냅샷은 문서 본문 저장 모델의 대체재가 아니라 파생 저장물이다.
또한 스냅샷은 live document read의 다른 표현이 아니라 authoring/recovery artifact이므로 public read에 열지 않고, create/describe/open/delete 전부 writable principal 기준으로만 허용한다.

### 휴지통과 영구 삭제

현재 정책은 문서 휴지통과 영구 삭제를 구분한다.

- 문서를 `trash`로 옮길 때는 attachment와 snapshot을 그대로 유지한다.
- 문서를 복구하면 기존 attachment와 snapshot binding을 계속 사용하고, binding 상태도 `TRASHED -> ACTIVE`로 복귀한다.
- document hard delete와 block delete는 resource를 즉시 hard purge하지 않고 `PENDING_PURGE`로 보낸다.
- purge scheduler가 grace period 이후 content store와 catalog를 실제 정리하고 binding을 `PURGED`로 전환한다.
- block delete, document hard delete, trash 만료 후 purge처럼 영구 정리 성격의 경로에서만 resource cleanup을 수행한다.

즉 휴지통은 "잠시 숨긴 상태"로 보고, 외부 리소스도 그 기간 동안 같이 보존한다.

## 4. `document_resources`의 역할

`document_resources`는 descriptor attribute를 보완하는 참조 모델이다.

현재 용도는 다음과 같다.

- 문서와 외부 리소스 관계의 기준 원천
- 첨부파일/스냅샷의 경로 소속 검증
- 문서 삭제/블록 삭제 시 cleanup 대상 조회
- status 기반 정리 ledger
- soft delete 이력 유지
- 휴지통 복구 시 기존 resource 연결 재사용

현재 주요 컬럼은 아래와 같다.

- `resource_id`
- `resource_kind`
- `owner_user_id`
- `usage_type`
- `status`
- `document_version`
- `deleted_at`
- `purge_at`
- `last_error`
- `repaired_at`

descriptor attribute에도 `documentId`, `blockId`, `ownerUserId` 같은 값은 남기지만, 조회와 정리 기준은 `document_resources`가 먼저 담당한다.
이 테이블은 문서/블록 live aggregate 관계보다 cleanup ledger 역할을 우선하므로, 문서 hard delete 뒤에도 purge와 repair 단서를 유지할 수 있어야 한다.

현재 binding 상태는 다음처럼 사용한다.

- `ACTIVE`
  - 정상 문서/블록이 참조 중인 상태
- `TRASHED`
  - 문서가 휴지통에 들어가 리소스는 유지하지만 live surface에서는 숨긴 상태
- `PENDING_PURGE`
  - resource soft delete가 끝났고, 실제 content/catalog purge를 기다리는 상태
- `PURGED`
  - purge scheduler가 실제 정리를 끝낸 상태
- `BROKEN`
  - binding과 catalog 사이 drift가 감지되어 수동 점검이 필요한 상태

## 5. 트랜잭션 경계

현재 구조는 "문서/블록 DB 트랜잭션"과 "resource lifecycle 전달"을 분리해서 본다.

### DB canonical 경계

- 문서/블록 생성, 수정, 이동, 삭제
- `document_resources` row 저장과 status 전이

이 부분은 애플리케이션 로컬 DB 트랜잭션 안에서 처리된다.

### resource 처리 경계

- `ResourceService.store(...)`
- `ResourceService.delete(...)`
- resource catalog/outbox 반영
- relay를 통한 후속 publish

이 부분은 `platform-resource`와 outbox 정책을 따른다.

중요한 점은 현재 구조가 file-storage까지 포함한 end-to-end XA를 제공하지 않는다는 것이다.
즉 resource 저장과 binding row 저장은 같은 유스케이스 안에서 호출되지만, 완전한 분산 트랜잭션으로 묶이지는 않는다.

따라서 운영 관점에서는 아래 전제가 필요하다.

- orphan resource 정리 경로가 필요하다.
- lifecycle 전달은 즉시 publish가 아니라 outbox relay를 기준으로 본다.
- 삭제 후 실제 content purge는 별도 scheduler로 지연 처리한다.
- binding과 catalog drift는 reconcile job으로 주기적으로 점검한다.

## 6. 현재 운영 교체 방식

현재 스키마 반영 방식은 환경별로 다르다.

- dev
  - `ddl-auto=update`
- test
  - `ddl-auto=create-drop`
- prod
  - `ddl-auto=none`
  - `platform.resource.jdbc.initialize-schema=false`

즉 로컬과 테스트는 엔티티 기준 자동 생성으로 빠르게 맞추지만, 운영은 자동 변경에 기대지 않고 새 구조를 직접 적용한다.

현재 상태를 정확히 말하면 다음과 같다.

- 코드와 테스트에는 목표 스키마가 반영돼 있다.
- 운영은 자동 변경에 기대지 않고, 기존 구조를 고려하지 않은 새 스키마 직접 교체 방식으로 반영한다.
- 현재 전제에서는 `document_resources`, `platform_resource_catalog`, `platform_resource_outbox`를 새 기준으로 바로 생성하면 된다.
- `document -> document-snapshot` kind rename, binding backfill, 레거시 데이터 이행 절차는 고려하지 않는다.

운영 스케줄러와 단발성 실행 구성은 아래와 같다.

- `DocumentsResourceLifecycleRelay`
  - outbox pending 이벤트 전달
- `DocumentsResourcePurgeScheduler`
  - `PENDING_PURGE` binding 실제 정리
- `DocumentsResourceReconcileScheduler`
  - binding과 catalog drift 점검 및 안전한 범위 자동 복구
- `DocumentsResourceBackfillRunner`
  - `resource-migration` profile에서 단발성 reconcile/purge 실행

이 문서의 요지는 한 가지다.

editor-service는 지금 "DB canonical 문서 편집 모델"과 "platform-resource 외부 리소스 모델"을 함께 사용하고 있으며, 둘 사이 연결은 `document_resources`와 outbox relay를 기준으로 운영한다.
