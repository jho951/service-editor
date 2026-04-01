# 2026-03-16 Auth Entry And Validation Minimization

## Step 1. 작업 요약

- 목적: 서비스 진입 인증 전제와 `@Valid` 이후 요청 파싱 최소화 원칙을 공통 규칙으로 반영한다.
- 요구사항 변경: `docs/REQUIREMENTS.md`에 로그인하지 않은 사용자는 gateway에서 차단된다는 전제와, 선언적 검증 이후 중복 null/빈값 보정을 최소화하는 규칙을 추가했다.
- 구현 조정: Workspace 생성 서비스에서 `@Valid` 이후 불필요한 `name` null 방어 분기를 제거했다.
