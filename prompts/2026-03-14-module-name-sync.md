## 2026-03-14
- 작업 목적: `settings.gradle` 기준으로 멀티모듈 이름과 실제 디렉터리/참조를 일치시킴
- 핵심 변경: `drawing-*` 디렉터리를 `documents-*`로 변경하고 Gradle project dependency, Docker 경로, 앱 식별자 반영
- 문서 갱신: `docs/REQUIREMENTS.md`, `docs/decisions/001-multi-module-structure.md`, `docs/runbook/DEBUG.md` 동기화
- 비고: IntelliJ `.idea` 모듈 경로와 이름도 함께 정리
