# 2026-03-18 Block Update And Move

## Step 1. 수정 API와 이동 API 책임 분리

- 목적: 블록 수정 API를 내용/메타데이터 수정과 이동 API로 명확히 분리한다.
- 변경 내용: `PATCH /v1/blocks/{blockId}`는 내용/메타데이터 수정만 담당하고, `POST /v1/blocks/{blockId}/move`를 단일 이동 API로 정의했다.
- 문서 반영: `docs/REQUIREMENTS.md`, ADR, 관련 discussion 문서를 갱신했다.

## Step 2. 블록 수정 API 구현

- 목적: 분리된 수정 API를 내용 수정 전용으로 구현한다.
- 변경 내용: 블록 수정 DTO, `PATCH /v1/blocks/{blockId}`, 서비스 `update(...)`, WebMvc/서비스/통합 테스트를 추가했다.
- 핵심 변경: 초기 계약은 `text`, `version`을 받고 `updatedBy`는 헤더 기반으로 서버에서 반영하도록 연결했다.
- 충돌 정책: 현재 블록 version과 요청 version이 다르면 `409 Conflict`를 반환하도록 검증을 추가했다.

## Step 3. 블록 이동 API 구현

- 목적: 블록 이동과 순서 변경을 별도 API로 구현한다.
- 변경 내용: `MoveBlockRequest`, `POST /v1/blocks/{blockId}/move`, 서비스 `move(...)`, WebMvc/서비스/Boot 통합 테스트를 추가했다.
- 구현 내용: 활성 블록 조회, version 충돌 검증, 같은 문서 부모 검증, 자기 자신 부모 금지, 하위 블록 순환 이동 금지, anchor 기반 `sortKey` 계산, no-op 허용, `updatedBy` 갱신 로직을 구현했다.
- 정렬 정책: `OrderedSortKeyGenerator`를 재사용하고 gap 고갈은 `SORT_KEY_REBALANCE_REQUIRED`로 변환했다.

## Step 4. 이동 깊이 제한 보강

- 목적: 생성과 이동의 깊이 정책을 일치시킨다.
- 변경 내용: `BlockServiceImpl.move(...)`에 `validateDepth(targetParentBlock)`를 추가했다.
- 테스트 보강: `BlockServiceImplTest`, `BlockApiIntegrationTest`에 최대 깊이 초과 부모로 이동할 때 `INVALID_REQUEST`를 반환하는 케이스를 추가했다.
