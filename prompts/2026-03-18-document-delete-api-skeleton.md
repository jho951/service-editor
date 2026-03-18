# 2026-03-18 문서 삭제 API 스켈레톤

- 작업 목적: 문서 삭제 API 엔드포인트와 서비스 시그니처 추가
- 변경 범위: `documents-api` 컨트롤러, `documents-core` 서비스 인터페이스, `documents-infrastructure` 최소 구현
- 제외 범위: soft delete 비즈니스 로직, block 연계 삭제
- 검증 방식: Gradle `compileJava`로 모듈 컴파일 확인
