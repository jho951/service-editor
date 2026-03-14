## 2026-03-14
- 작업 목적: GitHub에 올라가면 안 되는 로컬/민감 설정 파일을 `.gitignore`에 반영
- 핵심 변경: `.env` 계열, `gradle.properties`, 로컬 application override, secret/log 파일 패턴 추가
- 비고: 기존 IDE/빌드 산출물 ignore 규칙은 유지
