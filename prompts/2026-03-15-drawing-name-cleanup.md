# 2026-03-15 drawing 명칭 정리

- 작업 목적: Drawing 레포지터리 마이그레이션 이후 남아 있던 `Drawer`, `drawing`, `drawerOpenAPI` 등 레거시 명칭 제거
- 핵심 변경: `documents-core`, `documents-infrastructure`, `documents-api`에서 `Drawer` 계열 타입과 참조를 `Document` 기준으로 정리
- 설정 반영: `application-db.yml`, `application-dev.yml`, `docker/docker-compose.yml`의 DB/컨테이너 식별자를 `documents` 기준으로 유지 정리
- 문서 반영: `docs/REQUIREMENTS.md`, `docs/decisions/002-use-jpa-instead-of-mybatis.md`, `docs/runbook/DEBUG.md`를 현재 명칭과 맞춤
