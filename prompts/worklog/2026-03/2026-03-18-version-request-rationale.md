# 2026-03-18 Version Request Rationale

## Step 1. 작업 요약

- 작업 목적: request에 version을 포함하는 이유를 lost update 시나리오로 문서화
- 범위: `docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md`, `docs/REQUIREMENTS.md`
- 핵심 변경: 서버가 JPA `@Version`을 관리하더라도 클라이언트가 본 version이 없으면 stale update 검출이 어렵다는 시나리오 추가
- 추가 정리: request에 `version`을 넣는 이유를 A/B 동시 수정 예시로 설명
