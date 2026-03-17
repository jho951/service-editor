# 2026-03-16 Document SortKey Not Null

- 목적: 문서 계층 정렬 키를 필수값으로 고정하고 생성 시 자동 발급 규칙을 반영한다.
- 요구사항 변경: `docs/REQUIREMENTS.md`에서 `Document.sortKey`를 nullable에서 필수로 변경하고 sibling scope 유일성 규칙을 명시했다.
- 구현 변경: 문서 생성 시 `(workspaceId, parentId)` 기준 `MAX(sortKey)`를 조회해 다음 고정폭 숫자 문자열을 발급하도록 서비스와 저장소를 수정했다.
- 검증 변경: API, 서비스, 스키마 테스트에서 `sortKey` 자동 부여와 `sort_key NOT NULL`을 검증하도록 보강했다.
