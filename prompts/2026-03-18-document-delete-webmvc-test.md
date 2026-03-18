# 2026-03-18 문서 삭제 WebMvc 테스트

- 작업 목적: 문서 삭제 API의 WebMvc 테스트를 기존 `DocumentControllerWebMvcTest`에 추가
- 변경 범위: `documents-api` 기존 WebMvc 테스트 확장, 잘못 만든 `documents-boot` 테스트 제거
- 포함 케이스: 정상 삭제, 문서 없음, 이미 삭제됨, 인증 헤더 누락
- 검증 방식: 대상 WebMvc 테스트 실행
