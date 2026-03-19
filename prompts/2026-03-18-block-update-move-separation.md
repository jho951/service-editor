# 2026-03-18 Block Update Move Separation

- 작업 목적: 블록 수정 API를 이동 API와 분리하는 방향으로 문서 정책 정리
- 범위: `docs/REQUIREMENTS.md`, ADR, 관련 discussion 문서 갱신
- 핵심 변경: `PATCH /v1/blocks/{blockId}`는 내용/메타데이터 수정만 담당, `POST /v1/blocks/{blockId}/move`를 단일 이동 API로 정의
- 추가 정리: 블록 이동은 drop 시점 단일 command로 처리하고 `sortKey` 재계산 책임을 이동 API로 한정
