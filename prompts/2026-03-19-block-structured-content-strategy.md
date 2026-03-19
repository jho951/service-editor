# 2026-03-19 Block Structured Content Strategy

- 작업 목적: `Block.text`를 구조화 JSON 본문으로 전환하는 방향과 복잡도를 검토했다.
- 핵심 판단: `text -> props/content` 전환은 단순 필드 추가가 아니라 본문 모델 전환이며, 핵심 난점은 JSON 컬럼보다 동시성 의미 변화다.
- 권장안: 블록 단위 데코레이션은 채택하지 않고, 구조화 `content` 모델 + 초기에는 `block.version` optimistic lock으로 시작한다.
- 후속 권장: 추후 autosave/협업 확장을 위해 transaction/op 기반 저장으로 단계적으로 이동한다.
- 추가 정리: `BlockType`은 블록 바깥 타입, content 내부 `type`은 본문 포맷/노드 타입으로 분리한다.
- 문서 반영: `docs/discussions/2026-03-19-block-structured-content-strategy.md` 추가.
