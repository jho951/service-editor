# 2026-04-23

- 목적: Gateway 내부 JWT 계층과 현재 401/403 장애 시나리오를 문서와 troubleshooting에 반영한다.
- 배경: editor-service 문서 API 401과 authz-service caller proof 분리 이유를 운영 문서에서 바로 찾기 어려웠다.
- 핵심 변경:
  - `docs/runbook/troubleshooting.md` 추가
  - Gateway 내부 JWT와 authz caller proof JWT의 구분을 service-contract와 연동해 설명
  - 현재 장애 증상, 확인 포인트, 해결 방법 정리
