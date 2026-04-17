# ADR 020: transaction `BLOCK_CREATE`는 선택적 초기 content를 함께 받을 수 있다

## 상태

채택됨

## 배경

기존 transaction 계약은 `BLOCK_CREATE`가 위치만 확정하고, 새 블록의 본문은 같은 batch의 `BLOCK_REPLACE_CONTENT`가 맡도록 정의했다.

이 모델은 create와 replace_content의 역할 구분이 명확하다는 장점이 있었다.

하지만 실제 editor 저장 흐름에서는 새 블록 생성 후 바로 입력하는 경우가 매우 흔하다.
debounce 또는 `Ctrl+S` flush 시점에는 이미 브라우저 로컬 state에 새 블록의 최종 `content`가 올라와 있으므로, temp block에 대해 `BLOCK_CREATE + BLOCK_REPLACE_CONTENT`를 항상 별도로 유지하는 것은 불필요한 operation과 write를 늘릴 수 있다.

동시에 "블록 생성"과 "기존 블록의 후속 본문 수정"의 역할 차이 자체는 유지할 필요가 있다.

## 결정

- editor transaction의 `BLOCK_CREATE`는 위치 필드와 함께 선택적 `content`를 받을 수 있다.
- `BLOCK_CREATE.content`가 없으면 서버는 기존과 같이 empty structured content fallback을 저장한다.
- `BLOCK_CREATE.content`가 있으면 서버는 create 시점에 그 값을 새 블록의 초기 content로 저장한다.
- 기존 서버 block의 본문 변경은 계속 `BLOCK_REPLACE_CONTENT`가 담당한다.
- 새 temp block에 대한 `BLOCK_CREATE + BLOCK_REPLACE_CONTENT`는 프론트 queue가 flush 전에 `BLOCK_CREATE(content=latestContent)` 하나로 coalescing하는 것을 권장한다.
- 다만 서버는 temp block을 참조하는 `BLOCK_REPLACE_CONTENT`도 계속 허용해, 프론트가 모든 케이스를 완전히 접지 못해도 정상 처리할 수 있어야 한다.
- `BLOCK_REPLACE_CONTENT`는 여전히 block `content` 전체 교체 operation이며, range patch로 확장하지 않는다.

## 영향

- 장점:
- 새 블록 생성 후 바로 입력하는 흔한 경로에서 operation 수와 후속 write를 줄일 수 있다.
- autosave batch가 "로컬 최종 상태를 저장한다"는 성격을 더 직접적으로 반영할 수 있다.
- 기존 block 수정은 계속 `BLOCK_REPLACE_CONTENT`가 맡으므로 후속 수정 책임은 분리된다.

- 단점:
- `BLOCK_CREATE`의 의미가 위치 확정에서 "초기 상태를 가진 생성"까지 넓어진다.
- transaction DTO, 가이드, 테스트를 함께 갱신해야 한다.

## 관련 문서

- [2026-04-01-block-create-initial-content-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-04-01-block-create-initial-content-review.md)
- [2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-20-editor-transaction-dto-and-frontend-queue-spec.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
