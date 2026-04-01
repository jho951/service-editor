# 2026-03-17 Block List Read

## Step 1. 작업 요약

- 작업 목적: `docs/REQUIREMENTS.md` 기준으로 `GET /v1/documents/{documentId}/blocks` 문서 내 블록 목록 조회 API를 구현한다.
- 요구사항 변경: 있음. 현재 MVP는 기능 집중을 위해 문서 블록 전체 조회만 제공하고, `parentId` 기반 서브 블록 조회는 후속 정책 확정 시점으로 미뤘다.
- 결정 기록: 없음. 기존 블록 조회 범위 안의 API 계약 구체화이며 아키텍처/정책 변경은 아니어서 ADR은 추가하지 않았다.
- 구현 범위: 블록 조회 서비스 계약, 문서 전체 활성 블록 조회 저장소 쿼리, 컨트롤러 GET 엔드포인트, API/서비스/통합 테스트를 추가했다.
- 검증: `./gradlew :documents-api:test --tests com.documents.api.block.BlockControllerWebMvcTest`, `./gradlew :documents-infrastructure:test --tests com.documents.service.BlockServiceImplTest`, `./gradlew :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest`
