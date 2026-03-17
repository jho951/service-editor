# 2026-03-16 pk column naming

- 작업 목적: 엔티티 PK 물리 컬럼명을 `id`에서 `${domain}_id` 규칙으로 변경.
- 요구사항 변경: `REQUIREMENTS.md`에 식별자 컬럼 명명 규칙과 Workspace/Document/Block 설명을 보강.
- 구현 변경: `Workspace`, `Document`의 `@Column(name = ...)`를 각각 `workspace_id`, `document_id`로 수정.
- 검증 보강: H2 `INFORMATION_SCHEMA.COLUMNS` 기반 스키마 통합 테스트 추가.
