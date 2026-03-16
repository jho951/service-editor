# 2026-03-16 document read

- 작업 목적: `REQUIREMENTS.md` 기준으로 워크스페이스 문서 다건 조회와 문서 단건 조회를 구현.
- 핵심 변경: `GET /v1/workspaces/{workspaceId}/documents`, `GET /v1/documents/{documentId}` 추가.
- 구현 메모: soft delete 문서 제외, 워크스페이스 존재 검증, 안정 정렬(`sortKey`, `createdAt`, `id`) 적용.
- 검증: `:documents-api:test`, `:documents-infrastructure:test`, `:documents-boot:test --tests com.documents.api.document.DocumentApiIntegrationTest`.
