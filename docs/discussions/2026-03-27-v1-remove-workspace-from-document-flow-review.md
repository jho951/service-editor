# 2026-03-27 v1 문서 흐름에서 Workspace 제거 검토 메모

## 문서 목적

- v1 범위에서 Workspace를 활성 기능에서 제외할지 검토한다.
- 문서 생성/목록/휴지통 흐름을 Workspace 기준에서 사용자 기준으로 바꿀지 정리한다.
- 본 문서는 채택 전 검토 메모다.

## 배경

- 현재 문서 API는 `workspaceId`를 입력받아 문서 생성과 목록 조회를 수행한다.
- 그러나 제품과 화면 설계 기준에서 Workspace 개념, 진입 화면, 멤버십 정책, 전환 UX가 아직 확정되지 않았다.
- 반면 v1에서는 로그인 직후 사용자의 문서 목록을 바로 보여주는 흐름이 우선이다.

## 검토 범위

- 문서 API의 소유 기준을 Workspace에서 사용자로 바꾸는지 여부
- `Document`와 `Workspace`의 영속 연관관계를 v1에서 끊을지 여부
- Workspace API/코드를 삭제하지 않고 보류 상태로 남길지 여부

## 핵심 질문

1. v1 문서 흐름에서 Workspace를 활성 선행 리소스로 유지해야 하는가?
2. 문서 목록과 휴지통 목록은 어떤 기준으로 필터링해야 하는가?

## 선택지

### 선택지 1. Workspace 선행 구조 유지

#### 개요

- 현재처럼 `workspaceId`를 입력받아 문서 생성과 목록 조회를 유지한다.

#### 시나리오

1. 사용자가 로그인한다.
2. 프론트는 먼저 Workspace를 생성하거나 선택해야 한다.
3. 이후 `workspaceId`를 기준으로 문서 목록을 요청한다.

#### 장점

- 향후 멀티 워크스페이스 확장과 연결하기 쉽다.
- 문서 그룹의 루트 개념이 명시적이다.

#### 단점

- v1 화면과 제품 정책이 정해지지 않은 상태에서 선행 설계 부담이 크다.
- 로그인 직후 문서 진입 흐름이 불필요하게 길어진다.

### 선택지 2. v1은 사용자 소유 문서로 단순화

#### 개요

- 문서 생성/목록/휴지통은 `X-User-Id` 기반 사용자 소유 문서로 처리한다.
- Workspace API와 엔티티는 추후 재도입을 위해 코드상 백업만 유지한다.

#### 시나리오

1. 사용자가 로그인한다.
2. 프론트는 `GET /documents`로 바로 사용자 문서 목록을 조회한다.
3. 새 문서 생성은 `POST /documents`에서 `@CurrentUserId`를 `createdBy`로 저장한다.
4. 부모 문서가 있으면 같은 사용자 소유 문서인지 검증한다.

#### 장점

- v1 문서 진입 흐름이 단순하다.
- Workspace 미정 상태가 문서 기능을 막지 않는다.
- 현재 필요한 화면과 API를 빠르게 맞출 수 있다.

#### 단점

- 추후 Workspace 재도입 시 문서 소유 모델을 다시 확장해야 한다.
- 현재 `createdBy`가 사용자 소유 기준과 감사 필드를 동시에 맡는다.

## 비교 요약

- 선택지 1은 장기 구조에는 자연스럽지만, v1 제품 설계 미정 상태와 충돌한다.
- 선택지 2는 임시 단순화 성격이 있지만, 지금 필요한 문서 UX와 가장 직접적으로 맞는다.

## 추천 시나리오

- 사용자가 로그인 후 바로 자신의 문서 목록을 보고, 새 문서를 만들고, 휴지통까지 같은 사용자 기준으로 관리하는 흐름이 v1에 가장 적합하다.

## 현재 추천 방향

- 선택지 2를 채택한다.
- 이유:
  - Workspace 화면/정책이 미정인 상태에서 선행 리소스 의존을 제거할 수 있다.
  - 문서 목록 첫 화면을 `GET /documents`로 단순화할 수 있다.
  - 기존 Workspace 코드는 추후 재도입 설계를 위해 백업 상태로 남길 수 있다.

## 미해결 쟁점

1. 추후 Workspace 재도입 시 `createdBy`와 별도 `ownerId`를 분리할지 검토 필요
2. 문서 단건/수정/삭제의 사용자 소유 검증을 permission 정책과 어떻게 결합할지 추가 설계 필요

## 다음 액션

1. 문서 엔티티/리포지토리/서비스에서 Workspace FK 의존 제거
2. 문서 목록/생성/휴지통 API를 사용자 기준으로 변경
3. REQUIREMENTS, ADR, runbook, prompt log 갱신

## 관련 문서

- [018-remove-workspace-from-v1-document-flow.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/018-remove-workspace-from-v1-document-flow.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [2026-03-27-v1-remove-workspace-from-document-flow.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-v1-remove-workspace-from-document-flow.md)
