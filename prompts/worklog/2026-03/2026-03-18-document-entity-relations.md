# 2026-03-18 document entity relations

## Step 1. 작업 요약

- 작업 목적: `Document`의 `workspaceId`, `parentId`를 UUID 필드가 아닌 JPA 연관관계로 전환하고 기존 API 동작을 유지한다.
- 핵심 변경: `Document.workspace`/`Document.parent`/`Document.children` 매핑, `parent_id` self FK `ON DELETE CASCADE`, 서비스/리포지토리/테스트 픽스처를 연관관계 기준으로 정리했다.
- 추가 정리: `update()`에서 부모 재조회로 인한 이중 flush/version 증가를 제거했다.
- 문서 반영: `docs/REQUIREMENTS.md`, `docs/runbook/DEBUG.md`, `docs/decisions/009-map-document-hierarchy-with-jpa-associations-and-db-cascade.md` 갱신.
- 검증: `./gradlew :documents-api:test :documents-infrastructure:test :documents-boot:test --tests com.documents.api.document.DocumentApiIntegrationTest --tests com.documents.api.block.BlockApiIntegrationTest --tests com.documents.schema.PersistenceSchemaIntegrationTest`
