# 2026-03-18 문서 삭제 벌크 연산 정리

- 작업 목적: 문서 삭제와 문서 소속 블록 삭제를 repository 벌크 update 기반으로 정리
- 변경 범위: `DocumentRepository`, `BlockRepository`, `DocumentServiceImpl`, 관련 서비스 테스트
- 적용 정책: 문서 벌크 update 결과가 0건이면 `DOCUMENT_NOT_FOUND`
- 검증 방식: 관련 서비스 테스트와 compile 검증
