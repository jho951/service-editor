# 2026-03-18 문서 삭제 블록 위임 정리

- 작업 목적: `DocumentServiceImpl`의 블록 soft delete 직접 접근 제거
- 변경 범위: `BlockService` 계약 추가, `BlockServiceImpl` bulk update 위임, `DocumentServiceImpl` 의존 방향 정리
- 구현 포인트: block bulk soft delete는 `BlockRepository` 수정 쿼리로 처리
- 검증 방식: 관련 서비스 테스트와 compile 검증
