# 2026-03-19 Block Props Review

- 작업 목적: `Block.props` JSON 필드 도입 필요성과 구조 적합성을 현재 코드/문서 기준으로 검토했다.
- 핵심 판단: 현재 저장소는 `text` 기반 plain text block 모델이므로 `text`와 `props.text`를 함께 두는 구조는 부적절하다.
- 권장안: `text`를 canonical source로 유지하고, 필요 시 `props`는 optional metadata로만 제한한다.
- 문서 반영: `docs/discussions/2026-03-19-block-props-review.md` 추가, REQUIREMENTS/ADR은 미채택 상태라 보류.
