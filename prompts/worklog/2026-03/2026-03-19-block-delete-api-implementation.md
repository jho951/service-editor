# 2026-03-19 블록 삭제 API 구현

## Step 1. 작업 요약

- 작업 목적: `REQUIREMENTS.md` 기준으로 블록 soft delete API를 추가하고, 삭제 시 하위 블록까지 함께 soft delete 되도록 구현
- 변경 범위: `BlockController`, `BlockService`, `BlockServiceImpl`, `BlockRepository`, 관련 WebMvc/통합/서비스 테스트, `docs/runbook/DEBUG.md`
- 구현 요지: 삭제 대상 루트 블록을 활성 상태로 조회한 뒤 활성 하위 블록 ID를 재귀 수집하고, 동일한 `deletedAt`/`actorId`로 `blocks` bulk update 1회 수행
- 검증 결과: `./gradlew test --tests 'com.documents.service.BlockServiceImplTest' --tests 'com.documents.api.block.BlockControllerWebMvcTest' --tests 'com.documents.api.block.BlockApiIntegrationTest'` 통과
- 요구사항 문서 반영 여부: 기존 `REQUIREMENTS.md`에 블록 soft delete와 하위 블록 포함 정책이 이미 명시돼 있어 추가 수정 없이 구현만 반영
