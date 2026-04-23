# 2026-04-23

- 목적: Gateway가 재발급하는 내부 JWT 계약과 editor-service 검증 설정을 일치시킨다.
- 배경: `/v1/documents` 보호 요청이 editor-service에서 `The iss claim is not valid`로 401을 반환했다.
- 핵심 변경:
  - `application.yml`의 내부 JWT secret 우선순위를 `PLATFORM_SECURITY_JWT_SECRET -> JWT_SECRET`로 조정
  - dev/prod compose에 `PLATFORM_SECURITY_JWT_SECRET`, `PLATFORM_SECURITY_JWT_ISSUER`, `PLATFORM_SECURITY_JWT_AUDIENCE` 추가
  - `.env.example`, `docs/REQUIREMENTS.md`를 Gateway 내부 JWT 계약 기준으로 갱신
- 메모: 운영 환경에서는 Gateway의 `GATEWAY_INTERNAL_JWT_*`와 editor-service의 `PLATFORM_SECURITY_JWT_*`를 동일하게 관리해야 한다.
