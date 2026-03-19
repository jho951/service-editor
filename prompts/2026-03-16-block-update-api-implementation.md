# 2026-03-19 Block Update API Implementation

- 작업 목적: 블록 수정 API를 내용 수정 전용으로 구현
- 범위: 블록 수정 DTO, 컨트롤러 `PATCH /v1/blocks/{blockId}`, 서비스 `update(...)`, 웹/API/서비스 테스트 추가
- 핵심 변경: request는 `text`, `version`을 받고 `updatedBy`는 헤더 기반으로 서버에서 반영
- 추가 변경: 현재 블록 version과 요청 version이 다르면 `409 Conflict`를 반환하도록 충돌 검증 추가
