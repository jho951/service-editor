# ADR 008: ordered siblings에 gap 기반 lexicographic sort key 정책을 채택

## 상태

채택됨

## 배경

문서와 블록은 모두 같은 부모 아래에서 순서를 가져야 하고, 중간 삽입과 이동이 빈번하다.
단순 정수 순번 방식은 중간 삽입 시 뒤쪽 sibling 전체의 `sortKey`를 다시 매겨야 하므로, 쓰기 범위가 넓고 hotspot 구간에서 과도한 update가 발생할 수 있다.
반대로 생성/이동 시 새 항목의 `sortKey`만 바꾸는 방식은 쓰기 범위를 국소화할 수 있지만, gap 고갈과 재균형 정책을 함께 정의해야 한다.

## 결정

- ordered sibling 집합의 공통 `sortKey` 정책으로 gap 기반 lexicographic key를 채택한다.
- `sortKey`는 대문자 영숫자(base36)만 사용하는 고정폭 문자열로 저장한다.
- 정렬은 DB의 문자열 오름차순 정렬만으로 해석 가능해야 한다.
- append/prepend는 기본 stride를 우선 사용하고, 중간 삽입은 앞/뒤 키 사이의 정수 gap 중간값을 사용한다.
- 생성/이동 시 기존 sibling의 `sortKey`를 일괄 재배치하지 않는다.
- gap이 고갈되면 즉시 재배치하지 않고 `SORT_KEY_REBALANCE_REQUIRED` 충돌을 반환한다.
- rebalance는 후속 reorder API 또는 운영 관리 작업에서 명시적으로 수행한다.
- Block가 이 정책을 먼저 적용하고, Document도 후속 작업에서 같은 정책으로 이관한다.

## 영향

- 장점:
  - 블록/문서 삽입과 이동 시 단일 row 중심 갱신이 가능하다.
  - hotspot 구간에서도 전체 sibling 재정렬을 피할 수 있다.
  - 문서와 블록이 같은 ordered key 정책을 공유하게 되어 장기적으로 운영 규칙을 통일하기 쉽다.
- 단점:
  - gap이 고갈된 구간은 언젠가 rebalance가 필요하다.
  - 키가 사용자 친화적인 순번이 아니라 운영/디버깅 시 규칙 이해가 필요하다.
  - 문자열 기반 정렬 정책과 재시도 규칙을 서비스 계층에서 일관되게 관리해야 한다.
