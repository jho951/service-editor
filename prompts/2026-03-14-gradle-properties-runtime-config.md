## 2026-03-14
- 작업 목적: `.env`에 있던 로컬 실행 설정을 `gradle.properties` 기반으로 Gradle 실행 태스크에 적용
- 핵심 변경: 루트 `gradle.properties` 추가, `bootRun`/`test` 등 JVM 태스크에 환경변수 주입 로직 연결
- 문서 갱신: `docs/runbook/DEBUG.md`에 로컬 실행 기본 설정 위치 반영
- 비고: Docker 경로는 기존 `.env` 사용을 유지
