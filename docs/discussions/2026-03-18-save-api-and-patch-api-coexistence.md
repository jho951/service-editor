# 2026-03-18 저장 API와 PATCH API 공존 검토 메모

## 문서 목적

- 문서 편집기용 저장 API와 일반 수정 API가 왜 함께 필요할 수 있는지 정리한다.
- 처음 보는 사람도 이해할 수 있도록 저장과 수정의 책임 차이를 설명한다.
- 현재 저장소의 문서/블록 도메인과 향후 autosave, 협업 확장 가능성을 함께 고려한다.

## 배경

- 문서는 메타데이터와 블록 트리를 함께 가진다.
- 블록은 ordered block tree로 저장된다.
- 블록에는 `parentId`, `sortKey`, `version`이 있다.
- autosave 시나리오를 감당해야 한다.
- v1 범위에서는 실시간 협업은 제외되어 있지만, 향후 확장 가능성은 열어둬야 한다.

이 전제에서는 "수정"이라는 단어가 두 가지 전혀 다른 상황을 가리킨다.

1. 편집기 안에서 사용자가 계속 타이핑하고, 블록을 옮기고, 여러 블록을 지우는 흐름
2. API 사용자나 백오피스가 특정 문서 제목이나 특정 블록 속성 하나를 고치는 흐름

## 검토 범위

- 편집기 저장과 일반 PATCH의 책임 분리
- 둘 중 하나만 남겼을 때의 문제
- 현실적인 API 공존 구조 제안

## 한 줄 결론

- `저장 API`와 `수정 API`는 이름이 비슷해 보여도 책임이 다르다.
- 편집기 저장 경로는 `여러 변경을 한 번에 반영하는 transaction/save API`가 중심이 되어야 한다.
- 그와 별개로 `문서 PATCH`, `블록 PATCH` 같은 일반 수정 API는 운영, 관리, 외부 연동, 단건 속성 변경을 위해 유지하는 편이 실무적으로 유리하다.

## 먼저 용어 정리

### 저장 API

- 편집기 세션에서 발생한 여러 변경을 일정 주기 또는 명시적 저장 시점에 묶어서 반영하는 API
- 보통 autosave, `Ctrl+S`, page hide flush, retry queue와 연결된다
- 예: `POST /v1/documents/{documentId}/transactions`

### 수정 API

- 특정 리소스 하나의 속성 일부를 직접 바꾸는 일반 REST API
- 보통 폼 제출, 백오피스 수정, 스크립트 호출, 관리 기능과 연결된다
- 예: `PATCH /v1/documents/{documentId}`, `PATCH /v1/blocks/{blockId}`

## 왜 둘을 같은 것으로 보면 안 되는가

### 1. 편집기 저장은 단건 수정이 아니라 짧은 시간의 작업 묶음이다

문서 편집기에서 실제 사용자 행동은 보통 이렇게 흘러간다.

1. 블록 A 본문 수정
2. 블록 B 삭제
3. 블록 C 생성
4. 블록 D를 다른 부모 아래로 이동
5. 다시 블록 A 수정

이 모든 일이 3~5초 안에 일어날 수 있다.

이 흐름을 `PATCH /v1/blocks/{blockId}` 여러 번으로 잘게 쪼개 보내면 다음 문제가 생긴다.

- 네트워크 요청 수가 급격히 늘어난다.
- 중간 요청 일부만 성공했을 때 상태가 애매해진다.
- 클라이언트 retry 로직이 복잡해진다.
- 블록 간 정합성을 한 번에 보장하기 어렵다.
- 저장 성공 시점을 사용자에게 설명하기 어렵다.

반대로 저장 API로 묶으면 다음이 쉬워진다.

- "현재까지의 변경을 저장했다"는 의미가 선명해진다.
- 여러 블록 변경을 한 트랜잭션으로 처리할 수 있다.
- autosave와 manual save를 하나의 큐 모델로 통합할 수 있다.
- 실패 시 batch 단위 재시도와 충돌 처리 정책을 세울 수 있다.

즉 편집기 저장은 본질적으로 `transaction 처리`에 가깝고, 일반 PATCH는 `resource 부분 수정`에 가깝다.

## 왜 일반 수정 API도 여전히 필요한가

### 1. 편집기 밖의 수정 경로가 존재한다

모든 수정이 편집기에서만 일어나지는 않는다.

예:

- 문서 제목 변경
- 문서 아이콘 변경
- 문서 커버 변경
- 특정 블록의 상태나 타입 속성 변경
- 백오피스에서 문제 데이터 수동 보정
- 운영 스크립트나 테스트 fixture 수정

이런 작업에 매번 transaction API를 쓰는 것은 과하다.

예를 들어 제목만 바꾸는데도 이런 요청을 보내는 것은 어색하다.

```json
{
  "baseVersion": 42,
  "operations": [
    {
      "type": "document.update_title",
      "title": "새 제목"
    }
  ]
}
```

이 경우는 아래처럼 단건 PATCH가 훨씬 자연스럽다.

```json
{
  "title": "새 제목",
  "version": 42
}
```

### 2. 관리 도구와 외부 연동은 단건 API를 선호한다

운영자 도구, 백오피스, QA용 툴, 마이그레이션 스크립트, 외부 서비스 연동은 다음 특징이 많다.

- 편집기 세션 개념이 없다.
- local queue가 없다.
- 여러 op를 모아서 flush하지 않는다.
- 사람이 특정 필드만 빠르게 수정하고 싶어 한다.

이런 맥락에서는 일반 PATCH API가 더 단순하고 이해하기 쉽다.

### 3. 테스트와 디버깅이 쉬워진다

단건 PATCH API가 있으면 다음이 쉽다.

- 블록 1개 수정 동작 검증
- 문서 제목 수정 검증
- 버전 충돌 테스트
- 실패 케이스 재현
- Postman, curl, Swagger에서 수동 점검

transaction API만 있으면 모든 테스트가 "배치 저장" 문맥에 묶여서, 단순 케이스 확인도 오히려 무거워질 수 있다.

### 4. 도메인 책임이 분리된다

같은 변경이라도 의미가 다르면 API도 분리하는 편이 좋다.

- `PATCH /v1/documents/{documentId}`
- 문서 리소스 자체의 일반 수정
- `PATCH /v1/blocks/{blockId}`
- 블록 리소스 자체의 일반 수정
- `POST /v1/documents/{documentId}/transactions`
- 편집기 세션에서 나온 여러 변경의 일괄 저장

이렇게 나누면 API를 보는 사람도 "이건 리소스 수정용", "이건 에디터 저장용"이라고 바로 이해할 수 있다.

## 하나만 남기면 생기는 문제

### 경우 1. transaction/save API만 남기는 경우

#### 장점

- 편집기 경로가 단일화된다.
- 저장 모델이 일관된다.

#### 단점

- 제목, 아이콘, 커버 같은 단건 메타데이터 수정도 과도하게 무거워진다.
- 외부 연동과 운영 스크립트가 사용하기 불편하다.
- API 문서가 처음 보는 사람에게 덜 직관적이다.
- 단순한 수정도 operation 포맷을 이해해야 한다.

### 경우 2. PATCH API만 남기고 save API를 두지 않는 경우

#### 장점

- 표면적으로는 REST가 단순해 보인다.
- 단건 CRUD 구현은 쉽다.

#### 단점

- autosave 시 요청이 지나치게 많아진다.
- 여러 블록 수정의 원자성 보장이 어렵다.
- 타이핑, 삭제, 이동이 섞인 편집기 흐름을 담기 어렵다.
- 추후 협업 브로드캐스트나 journal 설계가 불리해진다.

## 현실적인 공존 구조

### 권장 구조

- `GET /v1/documents/{documentId}/content`
- 에디터 초기 진입용 조회
- `POST /v1/documents/{documentId}/transactions`
- 에디터 저장용
- `PATCH /v1/documents/{documentId}`
- 문서 메타데이터 일반 수정용
- `PATCH /v1/blocks/{blockId}`
- 블록 단건 수정용

### 책임 분리

- `content 조회`
- 문서와 블록 트리를 에디터가 한 번에 로드
- `transactions 저장`
- 여러 블록 변경을 묶어 저장
- `document PATCH`
- 제목, 아이콘, 커버, 부모 이동 같은 단건 문서 변경
- `block PATCH`
- 단일 블록 본문 수정, 이동, 속성 수정 같은 단건 변경

## 언제 어떤 API를 쓰는가

### 시나리오 1. 사용자가 본문을 계속 타이핑한다

- 프론트는 로컬 상태만 즉시 반영한다.
- 500ms~5초 구간의 pending operation을 모은다.
- 저장 시 `transactions` API로 보낸다.

### 시나리오 2. 사용자가 문서 제목만 바꾼다

- 제목 input blur 또는 엔터 시 `PATCH /v1/documents/{documentId}` 호출

### 시나리오 3. 운영자가 특정 블록 데이터만 수정한다

- 백오피스나 Swagger에서 `PATCH /v1/blocks/{blockId}` 호출

### 시나리오 4. 향후 협업 브로드캐스트를 붙인다

- 편집기 저장은 여전히 `transactions`를 중심으로 간다.
- 서버는 저장 성공한 operation 결과를 WebSocket/SSE로 브로드캐스트한다.
- 단건 PATCH는 일반 관리 경로로 그대로 유지한다.

## "폼처럼 일부만 PATCH를 날릴 일이 실제로 있나"에 대한 답

- 있다.
- 다만 편집기 본문 저장의 주력 경로가 아닐 뿐이다.

주로 다음에서 많이 쓴다.

- 문서 제목 수정
- 문서 아이콘/커버 수정
- 문서 부모 변경
- 블록 타입별 속성 수정
- 관리자 수동 보정
- 테스트/운영용 간단 수정

반대로 블록 본문 타이핑은 PATCH보다는 save/transaction 모델이 더 잘 맞는다.

## 설계 시 주의할 점

### 1. 같은 변경을 두 API에서 모두 허용할지 결정해야 한다

- 선택지 A
- `block PATCH`에서도 허용
- `transactions`에서도 허용
- 선택지 B
- 편집기 본문 수정은 `transactions`만 허용
- `block PATCH`는 운영성/관리성 목적의 제한된 수정만 허용

실무적으로는 A가 유연하지만, B가 책임이 더 선명하다.

### 2. 동시성 정책은 일관되어야 한다

- `document PATCH`와 `block PATCH`도 version 검증을 써야 한다.
- `transactions`도 `baseVersion`과 block version 검증을 써야 한다.
- 어떤 API로 들어오든 최종 정합성 규칙은 같아야 한다.

### 2-1. 문서 전체 version과 block 개별 version은 역할을 나눠야 한다

- `Document.version`은 문서 전체 상태의 대표 버전으로 쓰는 편이 가장 단순하다.
- 여기서 문서 전체 상태는 제목, 아이콘, 커버, 부모, 공개 상태, 문서 내부 block tree를 모두 포함한다.
- `Block.version`은 개별 block 수정과 이동 충돌을 정밀하게 검출하는 용도로 유지한다.
- 두 버전을 모두 유지하면 프론트는 "이 문서 화면 전체가 최신인가"와 "이 block 하나를 저장해도 되는가"를 서로 다른 기준으로 판단할 수 있다.

시나리오:

1. 사용자가 문서를 열어 `Document.version = 10`을 받는다.
2. 다른 사용자가 제목을 수정하면 `Document.version`은 `11`이 된다.
3. 또 다른 사용자가 block 하나를 수정하면 그 block의 `Block.version`은 증가하고, 문서 전체도 달라졌으므로 `Document.version`은 `12`가 된다.
4. 첫 번째 사용자는 여전히 version `10` 기준 화면을 보고 있으므로 문서 전체가 stale 상태라는 사실을 `Document.version` 하나로 감지할 수 있다.

즉 `Document.version`은 freshness 기준값이고, `Block.version`은 개별 block 충돌 검출값이다.

### 2-5. 왜 request에 version이 필요한가

서버가 JPA `@Version`으로 버전을 관리하더라도, 클라이언트가 어떤 버전을 보고 수정했는지 서버가 모르면 `lost update`를 막기 어렵다.

시나리오:

1. 현재 DB의 block version이 `5`이고 text는 `"오늘 회의"`다.
2. 사용자 A와 사용자 B가 같은 블록을 조회한다.
3. 두 사용자는 모두 version `5` 상태의 화면을 보고 각자 다른 내용을 작성해 두지만 바로 저장하지는 않는다.
4. 사용자 A는 `"오늘 회의 3시"`로 수정해서 먼저 저장한다.
5. DB의 block text는 `"오늘 회의 3시"`가 되고 version은 `6`이 된다.
6. 사용자 B는 여전히 예전 화면 `"오늘 회의"`를 보고 있다가 몇 초 후 `"오늘 회의 취소"`로 저장한다.

이때 사용자 B 요청에 "나는 version `5`를 보고 수정했다"는 정보가 없으면 서버는 최신 row를 다시 읽어 저장해 버릴 수 있다.

- 서버는 현재 DB의 최신 block(version `6`)을 조회한다.
- 사용자 B의 수정값 `"오늘 회의 취소"`를 적용한다.
- 저장 후 version은 `7`이 된다.

이 경우 사용자 A가 먼저 반영한 `"3시"` 수정이 사용자 B의 `"취소"` 저장으로 조용히 덮여써질 수 있다.

반대로 request에 version `5`가 들어오면 서버는 다음처럼 판단할 수 있다.

- 현재 DB version은 `6`
- 요청이 기준으로 삼은 version은 `5`
- stale update이므로 `409 Conflict` 반환

즉 핵심은 "version을 누가 관리하느냐"가 아니라 "이 요청이 어떤 시점의 상태를 기준으로 만들어졌는지 서버가 검증할 수 있느냐"다.

권장 request 예시:

```json
{
  "text": "수정된 내용",
  "version": 5
}
```

이 시나리오는 충돌 검출이 중요한 저장 경로나 구조 변경 경로에 특히 중요하다. 따라서 문서 메타 수정, 단건 block 수정, transaction 저장은 모두 요청 기준 version을 명시적으로 받아 검증하는 편이 일관된다.

### 2-2. no-op에서 version을 올리지 않는 이유

- 요청이 들어왔더라도 실제 저장 상태가 바뀌지 않았다면 새 version을 발급할 이유가 없다.
- 같은 공개 상태를 다시 저장하거나, block move 요청이 현재 위치와 동일하거나, block content가 기존과 완전히 같다면 no-op으로 보는 편이 자연스럽다.
- no-op에서 version을 올리지 않으면 프론트가 불필요하게 stale 상태로 오인하지 않고, 저장 queue도 더 안정적으로 동작한다.

시나리오:

1. 사용자가 문서 공개 상태를 이미 `PRIVATE`인 문서에 다시 `PRIVATE`로 저장한다.
2. 서버가 이 요청에도 새 `Document.version`을 발급하면 다른 클라이언트는 "문서 전체가 바뀌었다"고 오해할 수 있다.
3. 실제 상태가 변하지 않았다면 version을 유지하는 편이 서버 의미와 프론트 해석이 더 잘 맞는다.

### 2-3. 공개 상태 변경도 문서 전체 version에 포함해야 한다

- 공개 상태는 문서 메타데이터의 일부이면서, 사용자에게 노출되는 문서 상태 자체를 바꾸는 변경이다.
- 따라서 제목 변경, 아이콘 변경과 같은 수준으로 `Document.version`에 포함하는 편이 일관된다.
- 공개 상태만 바뀌고 version이 유지되면 프론트는 문서가 최신인지 판단하기 어렵다.

시나리오:

1. 사용자 A가 문서를 열어 `Document.version = 20`을 본다.
2. 사용자 B가 같은 문서를 `PRIVATE`에서 `PUBLIC`으로 바꾼다.
3. 문서 본문과 제목은 그대로여도, 사용자 A가 보고 있는 문서 상태는 더 이상 최신이 아니다.
4. 이때 `Document.version`이 `21`로 증가해야 프론트가 최신 상태 재조회 필요성을 감지할 수 있다.

### 2-4. 프론트는 어떤 version을 기준으로 저장해야 하는가

- 문서 진입 시: 문서 조회 응답의 `Document.version`
- 문서 메타 수정 성공 후: 수정 응답의 `Document.version`
- transaction 저장 성공 후: transaction 응답의 `documentVersion`
- 단건 block API 응답에 문서 version이 없으면: 가장 안전한 기준값은 마지막 문서 조회 응답 또는 transaction 응답의 문서 version

즉 프론트의 화면 전체 기준값은 항상 `Document.version` 계열 응답으로 관리하고, `Block.version`은 개별 block 편집 충돌 검출용으로만 사용해야 한다.

### 3. 감사 로그와 히스토리는 저장 API를 중심으로 잡는 편이 좋다

- 사용자 편집 이력은 보통 transaction 단위가 더 의미 있다.
- 단건 PATCH는 관리성 이벤트 성격이 더 강할 수 있다.

## 현재 추천 방향

- 편집기 저장은 `POST /v1/documents/{documentId}/transactions`로 간다.
- 문서 메타데이터용 `PATCH /v1/documents/{documentId}`는 유지한다.
- 블록 단건용 `PATCH /v1/blocks/{blockId}`도 유지하되, 편집기 메인 경로로 보지는 않는다.

## 미해결 쟁점

1. 블록 본문 수정은 `transactions` 전용으로 제한할지, `block PATCH`도 허용할지
2. 문서 제목 수정은 에디터 저장 큐에 포함할지, 별도 `document PATCH`로 분리할지
3. transaction 실패 시 partial apply를 허용할지, 전부 rollback할지
4. 장기적으로 revision history를 snapshot 중심으로 할지, journal 중심으로 할지

## 다음 액션

1. 편집기 저장과 일반 수정의 허용 범위를 정한다.
2. 선택이 확정되면 ADR과 REQUIREMENTS에 반영한다.
3. 구현 단계에서는 version 정책과 감사 로그 기준을 함께 정한다.

## 관련 문서

- [블록 저장 API 검토 메모](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-18-block-save-api-review.md)
- [블록 저장 API 전략 검토 메모](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-18-block-save-api-strategy.md)
- 작업 로그: `prompts/2026-03-18-save-api-and-patch-api-coexistence.md`
