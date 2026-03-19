# 2026-03-19 문서 삭제 API 통합 테스트

- 작업 목적: 문서 삭제 API의 soft delete 통합 검증 추가
- 변경 범위: `DocumentApiIntegrationTest`에 삭제 성공/조회 실패/문서 없음 케이스 추가
- 검증 포인트: 문서 deletedAt 저장, 소속 활성 블록 soft delete, 다른 문서 블록 보존
- 검증 방식: `DocumentApiIntegrationTest` 대상 실행
