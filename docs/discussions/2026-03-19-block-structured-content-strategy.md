# 2026-03-19 block structured content 전략 검토 메모

## 문서 목적

- `Block.text`를 구조화 JSON 본문으로 전환할 때의 영향 범위를 정리한다.
- 블록 단위 스타일 대신 텍스트 범위 단위 데코레이션이 필요한 전제를 기준으로 검토한다.
- 현재 저장소에서 감당 가능한 동시성 전략의 시작점과 확장 경로를 제안한다.

## 배경

- 현재 저장소는 `Block.text`를 TEXT 블록 본문의 canonical source로 사용한다.
- 현재 요구사항은 v1에서 plain text block editor를 전제로 작성되어 있다.
- 하지만 실제 에디터 요구는 블록 전체 스타일보다 텍스트 일부 범위 스타일링에 더 가깝다.
- 이 전제를 받아들이면 `props.style` 같은 블록 단위 데코레이션보다 구조화 본문 모델이 더 자연스럽다.

## 검토 범위

- `text -> structured content` 전환 시 도메인/API/동시성 영향
- JSON 필드 구조의 현실적인 시작안
- 현재 저장소에서 먼저 채택할 수 있는 충돌 처리 수준

## 핵심 질문

1. `text`를 `props` 안으로 옮기는 것이 단순 필드 이동인가, 본문 모델 전환인가
2. 부분 스타일링을 감당하려면 어떤 JSON 구조가 필요한가
3. 현재 `block.version` 기반 낙관적 락이 어디까지 유효한가

## 고려한 자료와 사례

- `docs/REQUIREMENTS.md`
- `docs/discussions/2026-03-18-block-save-api-strategy.md`
- `docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md`
- 현재 `Block` 엔티티, `BlockController`, `BlockServiceImpl`

## 한 줄 결론

- `text`가 원래 `props` 안에 있어야 한다면, 이건 단순 복원이 아니라 본문 모델 전환이다.
- 구현 자체는 할 수 있지만, 동시성 의미가 바뀌므로 "JSON 컬럼 추가"보다 "편집 충돌 단위 재정의"가 핵심 문제다.
- 지금 저장소에서 가장 현실적인 시작점은 `structured content + block.version optimistic lock`이며, 이후 transaction/op 기반으로 확장하는 것이다.

## 왜 블록 단위 스타일이 맞지 않는가

- 사용자는 보통 블록 전체가 아니라 텍스트 일부만 굵게, 밑줄, 색상 처리한다.
- 같은 블록 안에서 `"Hello world"` 중 `"world"`만 굵게 만드는 요구는 블록 단위 `style.bold=true`로는 표현할 수 없다.
- 따라서 `props.style.bold` 구조는 간단해 보여도 실제 에디터 요구와 어긋난다.

## 권장 모델 방향

### 이름

- 본문 canonical source는 `props`보다 `content` 같은 이름이 더 적합하다.
- `props`는 장기적으로 블록 메타데이터와 본문을 섞어 놓는 이름이 되기 쉽다.

### `BlockType`과 JSON 내부 `type`의 관계

- 현재 `BlockType`은 블록의 바깥 레벨 종류를 의미한다.
- 예: `TEXT`, 향후 `IMAGE`, `CODE`, `CHECKBOX`
- 반면 JSON 내부 `type`은 본문 표현 포맷 또는 내부 노드 종류를 의미해야 한다.
- 예: `rich_text`, `paragraph`, `text`, `mention`
- 둘은 같은 값이 아니다.
- 따라서 바깥의 `Block.type`은 별도 컬럼/필드로 유지하고, JSON 내부 `type`은 content schema 안에서만 사용해야 한다.
- `Block.type=TEXT`인데 `content.type=rich_text`인 구조는 자연스럽다.
- 반대로 `Block.type`을 없애고 JSON 내부 `type`만으로 블록 종류까지 표현하면 블록 조회, 검증, 타입별 정책 분기가 불명확해진다.

### 시작 구조 예시

```json
{
  "type": "rich_text",
  "segments": [
    {
      "text": "Hello ",
      "marks": []
    },
    {
      "text": "world",
      "marks": [
        {
          "type": "bold"
        },
        {
          "type": "textColor",
          "value": "#000000"
        }
      ]
    }
  ]
}
```

### 이유

- 부분 스타일링이 가능하다.
- 단순 plain text도 segment 1개로 표현할 수 있다.
- 링크, 멘션, inline code 같은 확장 포인트를 넣기 쉽다.
- 나중에 patch operation을 설계할 때 range/segment 기준을 잡을 수 있다.

## 선택지

### 선택지 1. `text` 유지, `props`는 보조 메타데이터만 추가

#### 개요

- 현재 구조를 최대한 유지한다.
- 리치 텍스트는 아직 범위 밖으로 둔다.

#### 시나리오

1. 사용자가 블록 본문을 수정한다.
2. 서버는 기존처럼 `text`만 갱신한다.
3. 블록 배경색 같은 소수의 부가 표현만 `props`에 저장한다.

#### 장점

- 가장 단순하다.
- 현재 테스트와 API를 크게 흔들지 않는다.

#### 단점

- 부분 스타일링 문제를 해결하지 못한다.
- 결국 다시 본문 모델을 바꿔야 한다.

#### 적합한 상황

- 리치 텍스트가 아직 확정 요구가 아닌 경우

### 선택지 2. 본문을 구조화 JSON으로 전환하되, 충돌은 블록 단위 optimistic lock으로 시작

#### 개요

- `text`를 제거하거나 보조 파생값으로 축소한다.
- 본문 저장은 구조화 JSON 하나로 통일한다.
- 충돌 처리는 당장은 현재처럼 `block.version` 비교로 시작한다.

#### 시나리오

1. 사용자 A가 같은 블록의 앞부분 텍스트를 수정한다.
2. 사용자 B가 같은 블록의 뒷부분 텍스트에 색상을 넣는다.
3. A가 먼저 저장하면 block version이 증가한다.
4. B는 이전 version으로 저장해 `409`를 받는다.
5. 클라이언트는 최신 본문을 다시 받아 로컬 변경을 재적용하거나 사용자에게 충돌을 알린다.

#### 장점

- 본문 모델을 빨리 정리할 수 있다.
- 부분 스타일링 요구를 수용할 수 있다.
- 추후 transaction/op 기반으로 확장 가능한 출발점이 된다.

#### 단점

- 같은 블록 안의 비충돌성 변경도 충돌로 처리될 수 있다.
- autosave 환경에서 block 단위 `409`가 더 자주 보일 수 있다.
- 클라이언트 rebase 부담이 생긴다.

#### 적합한 상황

- 리치 텍스트 방향은 확정됐지만, 아직 CRDT/OT 수준까지는 가고 싶지 않은 경우

### 선택지 3. 구조화 본문 + operation 기반 저장으로 바로 간다

#### 개요

- 본문 자체를 범위/segment 기반 operation으로 수정한다.
- `transactions`와 결합해 저장한다.

#### 시나리오

1. 사용자가 `"world"`에 bold를 추가한다.
2. 클라이언트는 전체 JSON을 덮어쓰지 않고 range/mark operation을 큐에 기록한다.
3. autosave 시 operation batch를 서버에 보낸다.
4. 서버는 baseVersion 기준으로 적용하고 실패 시 conflict detail을 돌려준다.

#### 장점

- 긴 autosave 세션과 향후 협업 확장에 가장 유리하다.
- 충돌 범위를 block 전체보다 더 세밀하게 줄일 수 있다.

#### 단점

- 현재 저장소 기준 구현 난이도가 가장 높다.
- 에디터 모델, 저장 API, conflict 처리, 재시도 정책이 함께 바뀐다.

#### 적합한 상황

- 편집기 중심 제품으로 빠르게 확장할 계획이 분명한 경우

## 복잡도가 올라가는 실제 지점

### 1. 엔티티/영속

- `Block.text` 단일 문자열에서 구조화 JSON 필드로 바뀐다.
- MySQL 운영 환경과 H2 테스트 환경에서 JSON 검증 전략을 맞춰야 한다.
- 현재 `DocumentJsonCodec`처럼 Jackson 기반 codec 또는 JPA converter가 필요하다.

### 2. API

- `CreateBlockRequest`, `UpdateBlockRequest`, `BlockResponse`가 모두 바뀐다.
- 단건 PATCH가 전체 `content` replace를 받을지, 부분 operation을 받을지 정해야 한다.
- Swagger 예시와 validation도 다시 설계해야 한다.

### 3. 서비스

- 길이 제한, 빈 문자열 허용 여부, plain text 추출 기준 같은 도메인 규칙을 다시 세워야 한다.
- 검색이나 정렬이 본문 문자열을 필요로 하면 파생 text를 별도로 유지할지 결정해야 한다.

### 4. 동시성

- 현재는 "문자열 1개 변경"이라 충돌 의미가 단순하다.
- 구조화 본문으로 바뀌면 같은 블록의 서로 다른 범위 수정도 같은 JSON 문서 충돌로 보인다.
- 따라서 block version만 유지하면 안전성은 확보되지만, 충돌 빈도와 사용자 체감 비용이 증가한다.

## 동시성 관점에서의 추천

### 지금 바로 감당 가능한 수준

- 서버는 우선 `block.version` 기반 optimistic lock을 유지한다.
- 단건 PATCH는 전체 `content` replace를 받는다.
- 충돌 시 `409`와 함께 현재 block version, 최신 content를 내려준다.
- 클라이언트는 최신 content 기준으로 재적용하거나 사용자 확인 후 다시 저장한다.

### 이후 확장 경로

1. 에디터 저장 주 경로를 `POST /v1/documents/{documentId}/transactions`로 옮긴다.
2. `block.replace_content`, `block.apply_marks`, `block.insert_text`, `block.delete_range` 같은 op를 정의한다.
3. 충돌 응답은 block 단위가 아니라 op 단위 정보까지 포함한다.
4. 그 이후에야 OT/CRDT 같은 협업 모델 검토가 의미를 가진다.

## 추천 시나리오

1. 지금 리치 텍스트 방향을 확정한다.
2. 본문 canonical source를 `text`에서 `content` JSON으로 바꾼다.
3. 서버는 당분간 block version optimistic lock만 유지한다.
4. 편집기 저장이 무거워지면 transaction batch API로 이동한다.
5. 실제 충돌 빈도가 높아질 때 operation 수준 충돌 제어를 도입한다.

이 순서가 현재 저장소와 향후 확장 사이의 균형이 가장 좋다.

## 현재 추천 방향

- 블록 단위 데코레이션 모델은 채택하지 않는다.
- `props.text` 중심이 아니라 구조화 `content` 모델을 채택하는 쪽이 더 일관된다.
- 다만 첫 단계에서는 CRDT/OT까지 가지 말고 block version optimistic lock으로 시작한다.
- 즉 "본문 모델은 먼저 바꾸고, 동시성 세분화는 뒤로 미루는" 단계적 접근을 추천한다.
- 로드맵은 `structured content + block 단위 optimistic lock`으로 MVP/V1를 먼저 배포하고, 이후 실제 사용 패턴과 충돌 빈도를 본 뒤 `transactions/op 기반 저장`, 필요 시 `OT/CRDT` 검토 순서로 가져간다.

## 미해결 쟁점

1. 본문 필드 이름을 `props`, `content`, `richText` 중 무엇으로 둘지
2. plain text 파생값을 별도 컬럼으로 유지할지
3. 단건 PATCH에서 전체 replace만 허용할지, 일부 operation도 허용할지
4. 충돌 시 서버가 최신 content 전체를 반환할지, diff 힌트까지 줄지

## 다음 액션

1. 제품 기준으로 리치 텍스트 범위를 어디까지 볼지 확정한다.
2. 채택 시 `docs/REQUIREMENTS.md`에서 plain text 정책을 structured content 정책으로 갱신한다.
3. API 모델과 동시성 정책이 요구사항 수준 변경이면 ADR을 추가한다.
4. 구현 전 `Block content schema + API 변경안 + conflict response` 초안을 만든다.

## 관련 문서

- [2026-03-19-block-props-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-19-block-props-review.md)
- [2026-03-18-block-save-api-strategy.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-18-block-save-api-strategy.md)
- [2026-03-18-save-api-and-patch-api-coexistence.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-18-save-api-and-patch-api-coexistence.md)
- [012-adopt-structured-text-content-and-staged-concurrency-roadmap.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md)
- [2026-03-19-block-structured-content-migration.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-19-block-structured-content-migration.md)
