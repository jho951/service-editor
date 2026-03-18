# 2026-03-17 Document Update API Skeleton

- 작업 목적: 문서 수정 API의 PATCH 엔드포인트 뼈대 추가
- 범위: `documents-api` 요청 DTO/컨트롤러, `documents-core` 서비스 인터페이스, `documents-infrastructure` 구현 시그니처
- 핵심 변경: `PATCH /v1/documents/{documentId}` 추가, `UpdateDocumentRequest` 추가, 서비스 `update(...)` 메서드 연결
- 검증 계획: `documents-api` 웹 MVC 테스트 실행으로 응답 엔벨로프와 라우팅 확인
