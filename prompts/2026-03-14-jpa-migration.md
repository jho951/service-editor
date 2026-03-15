# 2026-03-14 JPA Migration

- 작업 목적: 저장소의 MyBatis 기반 영속 계층을 Spring Data JPA 구조로 전환
- 핵심 변경: `documents-infrastructure`를 JPA Repository 기반으로 교체하고 `Drawer`, `BaseEntity`를 JPA 엔티티 규약에 맞게 수정
- 문서 반영: `README.md`, `docs/REQUIREMENTS.md`, `docs/runbook/DEBUG.md`, `docs/decisions/001-multi-module-structure.md`, `docs/decisions/002-use-jpa-instead-of-mybatis.md`
- 검증 계획: Gradle 테스트로 Spring 컨텍스트와 JPA/H2 기동 확인
