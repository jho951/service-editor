# 2026-03-16 Application Runtime Config

## Step 1. 작업 요약

- 목적: 애플리케이션 실행 시 필요한 로컬/도커 환경 설정을 정리하고 부팅 실패 원인을 제거한다.
- 요구사항 변경: 없음. 기존 실행 환경과 개발 편의 범위 안에서 설정만 정리했다.
- 결정 기록: 없음. 되돌리기 어려운 정책 변경 없이 로컬 실행 설정만 조정했다.
- 실행 환경 수정: MySQL JDBC URL의 `characterEncoding=utf8mb4`를 `characterEncoding=UTF-8`과 `connectionCollation=utf8mb4_unicode_ci` 조합으로 바꿔 IntelliJ/bootRun 부팅 실패를 해결했다.
- 로컬 실행 정리: Docker MySQL에 `documents/documents` 전용 계정을 사용하도록 로컬/도커 기본 DB 자격증명을 통일했다.
- 설정 정리: JDBC URL에서 `characterEncoding`을 제거하고 DB의 `utf8mb4` 설정을 신뢰하도록 단순화했다. JPA DDL 정책은 `dev=update`, `prod=none`으로 분리했다.
