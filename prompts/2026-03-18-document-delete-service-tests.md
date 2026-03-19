# 2026-03-18 문서 삭제 서비스 단위 테스트

- 작업 목적: `DocumentServiceImplTest`에 문서 삭제 단위 테스트 보강
- 변경 범위: `documents-infrastructure` 서비스 테스트 코드만 추가
- 포함 케이스: 활성 문서 삭제 호출, block soft delete 위임, 동일 actor/deletedAt 전달, 문서 없음, 이미 삭제됨
- 검증 방식: `DocumentServiceImplTest` 단독 실행
