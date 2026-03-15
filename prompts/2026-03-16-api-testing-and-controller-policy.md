# 2026-03-16 API Testing And Controller Policy

- 목적: API 테스트 위치와 컨트롤러 책임 범위를 저장소 공통 규칙으로 확정한다.
- 요구사항 변경: `docs/REQUIREMENTS.md`에 테스트 배치/실행 원칙과 Controller 책임 원칙을 추가했다.
- 결정 기록: `docs/decisions/004-place-api-integration-tests-in-boot-and-keep-controllers-thin.md`를 추가했다.
- 구현 반영: Workspace API가 서비스 계층에서 조회 실패를 처리하도록 리팩터링한다.
