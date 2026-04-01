# 2026-03-15 Hexagonal Layer Cleanup

## Step 1. 작업 요약

- 작업 목적: `core`에 섞여 있던 API 전용 `constant`, `dto`, `exception` 파일을 헥사고날 책임에 맞게 재배치
- 핵심 변경: 응답 코드, 공통 응답 DTO, 전역 예외를 `documents-api`로 이동하고 `documents-core`는 도메인/서비스 계약만 유지
- 문서 반영: `README.md`, `docs/decisions/001-multi-module-structure.md`
- 검증 계획: `./gradlew test`로 모듈 경계 정리 후 컴파일 및 테스트 확인
