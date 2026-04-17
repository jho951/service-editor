# 2026-04-17 Docs Implementation Sync

## Step 1. 구현 기준 불일치 점검

- 목적: 현재 구현 상황과 문서가 같은 기준을 설명하는지 확인한다.
- 확인 내용: `README.md`, `docs/REQUIREMENTS.md`, `docs/runbook/DEBUG.md`, `docs/explainers/ordered-sortkey-generator.md`, admin block API 구현을 대조했다.
- 판단: 큰 API 방향은 맞지만 일부 문서가 예전 경로와 정책을 유지하고 있었고, admin block 보조 API는 단건 operation 계약을 코드에서 직접 검증하지 않았다.

## Step 2. 현재 구현 기준 문서 최신화

- 변경 내용: `README.md`를 현재 구현된 문서/블록/editor API 기준으로 갱신했다.
- 변경 내용: [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)의 문서 삭제 설명을 hard delete 기준으로 맞추고, 현재 v1 conflict 응답이 공통 실패 응답만 반환한다는 기준을 반영했다.
- 변경 내용: [docs/runbook/DEBUG.md](https://github.com/jho951/Block-server/blob/dev/docs/runbook/DEBUG.md)의 block API 점검 경로를 `/admin/**`와 editor save 경로 기준으로 갱신했다.
- 변경 내용: [docs/explainers/editor-save-model.md](https://github.com/jho951/Block-server/blob/dev/docs/explainers/editor-save-model.md)의 conflict 응답 설명을 현재 공통 실패 응답과 재조회 기준으로 맞췄다.
- 변경 내용: [docs/explainers/ordered-sortkey-generator.md](https://github.com/jho951/Block-server/blob/dev/docs/explainers/ordered-sortkey-generator.md)의 sort key generator 사용 위치에 document 생성/이동을 추가했다.
- 변경 내용: [ADR 011](https://github.com/jho951/Block-server/blob/dev/docs/decisions/011-separate-block-update-from-move-api.md), [ADR 014](https://github.com/jho951/Block-server/blob/dev/docs/decisions/014-adopt-transaction-centered-editor-save-model.md)의 후속 기준을 현재 admin/editor operation 경계와 conflict 응답 기준으로 보정했다.
- 변경 내용: [docs/roadmap/v2/blocks/editor-conflict-response.md](https://github.com/jho951/Block-server/blob/dev/docs/roadmap/v2/blocks/editor-conflict-response.md)를 추가해 conflict 상세 응답 고도화 과제를 v2 후보로 분리했다.

## Step 3. Admin block 단건 operation 계약 보강

- 변경 내용: `AdminBlockController`가 `EditorSaveRequest.operations` 길이가 정확히 1개가 아니면 `INVALID_REQUEST`를 반환하도록 검증을 추가했다.
- 변경 내용: `AdminBlockControllerWebMvcTest`와 `AdminBlockApiIntegrationTest`에서 여러 operation 요청이 첫 operation으로 위임되던 기대를 제거하고, 단건 보조 API 계약 위반으로 실패하는 테스트로 바꿨다.
- 검증: `./gradlew test`
