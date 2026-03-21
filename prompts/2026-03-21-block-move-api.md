# 2026-03-21 블록 move API 구현

- 작업 목적: 블록 이동 및 순서 변경 API를 현재 멀티모듈 구조와 AGENTS 규칙에 맞춰 구현하고 검증 결과를 기록한다.
- 변경 범위: 블록 move 요청 DTO, 컨트롤러 엔드포인트, 서비스 계약, 서비스 이동 로직, WebMvc 테스트, 서비스 단위 테스트, Boot 통합 테스트
- 관련 요구사항: `docs/REQUIREMENTS.md`의 블록 이동 / 순서 변경, `docs/decisions/011-separate-block-update-from-move-api.md`

## Step 1. 웹 계층 계약 추가

- `documents-api`에 `MoveBlockRequest`를 추가하고 `POST /v1/blocks/{blockId}/move` 엔드포인트를 연결했다.
- 요청 필드는 `parentId`, `afterBlockId`, `beforeBlockId`, `version`으로 고정했다.
- 응답은 기존 move 계열 정책과 동일하게 `200 OK`와 `GlobalResponse.ok()`로 유지했다.

## Step 2. WebMvc 테스트 보강

- `BlockControllerWebMvcTest`에 루트 이동 성공, 다른 부모 아래 after anchor 이동 성공, 인증 헤더 누락, version 누락, `BLOCK_NOT_FOUND`, `CONFLICT`, `INVALID_REQUEST` 케이스를 추가했다.
- 서비스 구현 전 단계에서 웹 계층 계약과 예외 매핑을 먼저 고정했다.

## Step 3. 서비스 이동 로직 구현

- `BlockService`에 `move(UUID blockId, UUID parentId, UUID afterBlockId, UUID beforeBlockId, Integer version, String actorId)` 계약을 추가했다.
- `BlockServiceImpl`에 활성 블록 조회, version 충돌 검증, 같은 문서 부모 검증, 자기 자신 부모 지정 금지, 하위 블록 순환 이동 금지, anchor 기반 `sortKey` 계산, no-op 허용, `updatedBy` 갱신 로직을 구현했다.
- 정렬 키 계산은 기존 `OrderedSortKeyGenerator`를 재사용하고, gap 고갈은 `SORT_KEY_REBALANCE_REQUIRED`로 변환했다.

## Step 4. 서비스 단위 테스트 추가

- `BlockServiceImplTest`에 루트 이동, 같은 부모 내 reorder, 다른 부모 이동, no-op, 블록 없음, version 충돌, 부모 없음, 다른 문서 부모, 자기 자신 부모, 하위 블록 부모, 잘못된 anchor, 정렬 키 공간 부족 케이스를 추가했다.
- 구현 규칙이 테스트로 먼저 고정되도록 성공 케이스 후 실패 케이스 순서로 정리했다.

## Step 5. 통합 테스트 추가

- `BlockApiIntegrationTest`에 실제 HTTP -> Controller -> Service -> JPA 흐름을 검증하는 이동 시나리오를 추가했다.
- 루트 재정렬, 다른 부모 이동, same parent `afterBlockId` 이동, 없는 블록, 삭제된 블록, 낡은 version, 자기 자신 부모, 하위 블록 부모, 다른 문서 부모/anchor 케이스를 검증했다.

## Step 6. 테스트 실행 결과

- 실행 명령:
  - `./gradlew --no-daemon :documents-api:test --tests com.documents.api.block.BlockControllerWebMvcTest`
  - `./gradlew --no-daemon :documents-infrastructure:test --tests com.documents.service.BlockServiceImplTest`
  - `./gradlew --no-daemon :documents-api:test --tests com.documents.api.block.BlockControllerWebMvcTest :documents-infrastructure:test --tests com.documents.service.BlockServiceImplTest :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest`
- 결과:
  - `BUILD SUCCESSFUL`
  - WebMvc, 서비스 단위, Boot 통합 테스트 모두 통과

## Step 7. REQUIREMENTS 반영 여부

- 반영하지 않음
- `docs/REQUIREMENTS.md`에 이미 `POST /v1/blocks/{blockId}/move`, `parentId`, `afterBlockId`, `beforeBlockId`, `version`, `sortKey` 갱신, `updatedBy`/`version` 갱신 정책이 정의되어 있어 이번 작업은 기존 요구사항 범위 안의 구현으로 판단했다.

## Step 8. 관련 문서 경로

- ADR: `docs/decisions/011-separate-block-update-from-move-api.md`
- Explainer: `prompts/explainers/ordered-sortkey-generator.md`
