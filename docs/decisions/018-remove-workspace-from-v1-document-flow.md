# ADR 018: v1 문서 흐름에서 Workspace 선행 의존 제거

## 상태

채택됨

## 배경

- 현재 문서 API는 `workspaceId`를 기준으로 문서 생성, 목록 조회, 휴지통 조회를 수행하고 있다.
- 그러나 v1 범위에서는 Workspace 화면, 선택 UX, 멤버십/권한 정책, 전환 정책이 아직 확정되지 않았다.
- 반면 v1 사용자 경험은 로그인 직후 자신의 문서 목록을 바로 보는 흐름이 우선이다.

## 결정

- v1 문서 생성/목록/휴지통 흐름은 Workspace 기준이 아니라 인증된 `userId` 기준으로 동작한다.
- `Document`의 영속 Workspace FK 연관은 제거한다.
- 문서 목록/휴지통 목록 기본 필터는 `createdBy = currentUserId`를 사용한다.
- 문서 생성 시 `@CurrentUserId`를 `createdBy`, `updatedBy`로 저장한다.
- 부모 문서 검증과 문서 이동의 부모 검증은 같은 Workspace가 아니라 같은 사용자 소유 문서인지 기준으로 판단한다.
- 기존 Workspace 엔티티와 API 코드는 추후 재설계를 위한 백업 자산으로 남기되, v1 활성 흐름에서는 사용하지 않는다.

## 영향

- 장점:
  - 로그인 직후 `GET /documents`로 바로 진입 가능
  - Workspace 미정 상태가 문서 기능 구현을 막지 않음
  - 문서 생성/조회 API 계약 단순화
- 단점:
  - 추후 Workspace 재도입 시 문서 소유 모델 재조정 필요
  - 현재 `createdBy`가 소유 기준과 감사 필드를 함께 담당
