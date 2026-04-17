# Workspace 재도입 검토

## 목적

- v1에서 보류한 Workspace 개념을 추후 다시 도입할 때 필요한 후속 작업을 정리한다.

## 후속 과제

- 사용자 소유 문서와 Workspace 소속 문서 모델을 어떻게 분리할지 결정
- `createdBy`와 문서 소유 필드(`ownerId` 또는 `workspaceId`) 분리 여부 검토
- Workspace 생성/선택 화면과 로그인 직후 진입 UX 확정
- Workspace 멤버십과 permission-service 연동 기준 확정
- 문서 단건/수정/삭제의 소유 검증과 권한 검증 책임 분리

## 운영 고려사항

- Workspace 재도입 시 기존 문서 데이터 마이그레이션 전략 필요
- 사용자 단독 소유 문서를 기본 Workspace로 이관할지 정책 확정 필요
- 현재 백업 코드는 `backup/workspace/`에 보관되므로, 재도입 시 이 경로를 기준으로 복구 범위와 재사용 가능 여부를 다시 검토

## 열어둘 질문

- Workspace를 문서의 강한 소속 루트로 둘지, 선택 가능한 그룹 개념으로 둘지
- 개인 문서함과 팀 Workspace를 같은 도메인으로 표현할지 별도 모델로 분리할지
