# 2026-03-16 Global Exception Unification

- 목적: 비즈니스 예외를 `GlobalException` 단일 타입으로 통합한다.
- 요구사항 변경: `docs/REQUIREMENTS.md`에 `ErrorCode.XXX.exception()` 기반 예외 생성 원칙과 서비스 계층 예외 일원화를 반영했다.
- 구현 변경: `ResourceNotFoundException`을 제거하고, 공통 `ErrorCode`/`GlobalException`을 core로 이동했다.
- 적용 결과: 서비스 구현은 `ErrorCode` 기반으로 예외를 던지고, API는 `GlobalExceptionHandler`로 표준 응답을 유지한다.
