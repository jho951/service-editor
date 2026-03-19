# 2026-03-18 문서 삭제 서비스 로직

- 작업 목적: 문서 1건 soft delete와 소속 활성 블록 soft delete 구현
- 변경 범위: `documents-infrastructure` 서비스 로직, 서비스 단위 테스트 보강
- 적용 정책: 활성 문서만 삭제 가능, soft delete 문서는 `DOCUMENT_NOT_FOUND`, block soft delete 동시 처리
- 검증 방식: 관련 모듈 `test` 및 `compileJava` 실행
