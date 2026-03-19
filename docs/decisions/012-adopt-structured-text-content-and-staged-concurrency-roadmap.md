# ADR 012: TEXT 블록은 structured content를 사용하고 동시성은 단계적으로 확장한다

## 상태

채택됨

## 배경

기존 요구사항과 구현은 `Block.text` 단일 문자열을 TEXT 블록 본문으로 사용했다.

하지만 실제 에디터 요구는 블록 전체 스타일보다 텍스트 일부 범위 스타일링에 가깝다.
이 경우 다음 문제가 생긴다.

- 블록 단위 `style.bold=true` 같은 구조는 부분 스타일링을 자연스럽게 표현하지 못한다.
- `text`와 `props.text`를 함께 두면 본문 canonical source가 두 개가 된다.
- 향후 링크, 멘션, 색상, inline mark 확장을 고려하면 structured content 모델이 더 일관된다.

동시에 현재 제품 단계에서는 MVP/V1의 구현 복잡도와 운영 가능성도 고려해야 한다.

- 지금 바로 OT/CRDT까지 도입하면 저장 API, 충돌 해석, 재적용 전략이 한 번에 커진다.
- 반대로 구조화 본문 없이 plain text를 유지하면 본문 모델을 다시 갈아엎게 될 가능성이 높다.

## 결정

- TEXT 블록 본문 canonical source는 plain string이 아니라 structured content JSON으로 관리한다.
- 블록 바깥 타입은 기존 `Block.type`으로 유지하고, 현재는 `TEXT`만 지원한다.
- TEXT 블록의 `content`는 최소한 `format`, `schemaVersion`, `segments`를 갖는 rich text 구조를 사용한다.
- segment 단위 mark 목록으로 스타일을 표현한다.
- v1 허용 mark 타입은 `bold`, `italic`, `textColor`, `underline`, `strikethrough`다.
- `textColor` 값은 프론트가 바로 사용할 수 있는 `#RRGGBB` 형식 hex 문자열을 사용한다.
- 링크, 멘션, inline code 등 추가 mark는 v1 범위에 포함하지 않고 후속 확장 대상으로 남긴다.
- v1 동시성 정책은 block 단위 optimistic lock을 유지한다.
- 같은 블록 내부의 비중첩 수정이라도 v1 서버 충돌 판정 단위는 block 전체다.
- 편집기 저장 경로는 향후 `transactions` 및 operation 기반 모델로 확장 가능하게 설계한다.
- OT/CRDT는 V1 이후 실제 사용 패턴과 충돌 빈도를 확인한 뒤 검토한다.

## 영향

- 장점:
- 부분 스타일링 요구를 수용할 수 있다.
- `text`와 `props.text`의 이중 본문 모델을 피할 수 있다.
- 프론트 에디터가 segment/mark 기반으로 연동하기 쉬워진다.
- v1 구현 복잡도를 통제하면서도 OT/CRDT 확장 경로를 열어둘 수 있다.

- 단점:
- 기존 `text` 중심 API와 엔티티, 테스트, 요구사항 문서를 함께 수정해야 한다.
- 같은 블록 안의 독립적 수정도 v1에서는 block 단위 충돌로 처리될 수 있다.
- 향후 operation 기반 저장으로 확장할 때 API 계약 일부를 다시 넓혀야 할 수 있다.
