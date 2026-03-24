# 2026-03-24 컨트롤러 리팩토링 재적용

- 작업 목표: 블록 조회 책임을 문서 컨트롤러로 이동하고 블록 관리 API를 admin 경로로 재정리
- Step 1: `GET /v1/documents/{documentId}/blocks`를 `DocumentController`에 추가
- Step 2: `BlockController`를 `AdminBlockController`로 변경하고 생성/수정/이동/삭제를 `/v1/admin/**`로 재매핑
- Step 3: WebMvc 테스트를 현재 컨트롤러 책임과 URL 기준으로 정리
- Step 4: `docs/REQUIREMENTS.md`에 admin 블록 API 경로와 책임 분리를 반영
