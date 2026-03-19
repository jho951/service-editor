# 2026-03-19 Block Structured Content Documentation

- 작업 목적: 구현 전 `Block` structured content 본문 모델과 단계적 동시성 로드맵을 저장소 문서에 반영했다.
- 핵심 변경: `docs/REQUIREMENTS.md`를 plain text 기준에서 structured content + v1 mark 제한 + staged concurrency roadmap 기준으로 수정했다.
- ADR 추가: `docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md`
- mark 정책: v1 허용 mark는 `bold`, `italic`, `textColor`, `underline`, `strikethrough`로 정리했다.
- 후속 구현은 이 문서 기준으로 `Block.content` 도입과 API/서비스 변경을 진행한다.
