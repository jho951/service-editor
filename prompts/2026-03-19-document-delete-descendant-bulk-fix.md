# 2026-03-19 문서 삭제 하위 문서 bulk soft delete 보완

- 작업 목적: 문서 삭제 시 상위 문서만 soft delete 되던 누락을 수정하고, 활성 하위 문서와 각 문서의 블록까지 함께 삭제되도록 보완
- 변경 범위: `DocumentRepository`, `DocumentServiceImpl`, 문서 삭제 서비스 테스트, 문서 삭제 API 통합 테스트
- 구현 요지: 하위 문서를 재귀 수집한 뒤 동일한 `deletedAt`/`actorId`로 문서 bulk update 1회 수행, 이후 각 문서의 블록 soft delete 위임
- 검증 포인트: 하위 문서 soft delete 전파, 다른 문서 보존, `documents` soft delete update SQL 1회 실행
