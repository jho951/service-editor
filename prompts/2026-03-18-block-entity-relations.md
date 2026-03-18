# 2026-03-18 block entity relations

- 작업 목적: `Block`의 `documentId`, `parentId`를 UUID 필드가 아닌 JPA 연관관계로 전환하고 기존 API 동작을 유지한다.
- 핵심 변경: `Block.document`/`Block.parent`/`Block.children` 매핑, `document_id`와 `parent_id` FK `ON DELETE CASCADE`, 리포지토리/서비스/테스트 픽스처를 연관관계 기준으로 정리했다.
- 문서 반영: `docs/REQUIREMENTS.md`, `docs/runbook/DEBUG.md`, `docs/decisions/010-map-block-hierarchy-with-jpa-associations-and-db-cascade.md` 갱신.
- 검증: `./gradlew :documents-api:test :documents-infrastructure:test :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest --tests com.documents.schema.PersistenceSchemaIntegrationTest`
