# 2026-04-05 Node 도메인 추상화 검토 메모

## 작업 목적

- `Document`, `Block`, 추후 `Workspace`까지 `Node`로 통합하는 방향을 검토한다.
- 공통 흐름이 많은 현재 구조에서 어떤 수준의 공통화가 실제 유지보수성에 도움이 되는지 정리한다.
- 본 문서는 채택 전 설계 검토 메모다.

## 배경

- 현재 블록 서버는 `Document` 계층과 `Block` 트리를 각각 별도 엔티티로 관리한다.
- 두 도메인은 `parentId`, `sortKey`, 이동/재정렬, soft delete, 계층 조회처럼 공통점이 많다.
- 실제 코드도 공통 정렬 정책은 이미 [`OrderedSortKeyGenerator`](https://github.com/jho951/Block-server/blob/dev/docs/explainers/ordered-sortkey-generator.md)로 공유한다.
- 반면 문서와 블록은 권한/소유, version, 상위 검증, 콘텐츠/메타데이터 규칙이 다르다.
- 따라서 질문은 "공통화가 필요한가"보다 "공통화를 어느 레벨에 둘 것인가"에 가깝다.

### 참고한 자료와 사례

- 외부 공식/공개 자료
  - Notion Engineering, The data model behind Notion's flexibility  
    https://www.notion.so/The-data-model-behind-Notion-s-flexibility-6ec61e89477344ce892903b7469dc8dc?pvs=21
  - Notion API, Parent object  
    https://developers.notion.com/reference/parent-object
  - Notion API, Block object  
    https://developers.notion.com/reference/block
  - Notion API, Create a page  
    https://developers.notion.com/reference/post-page
  - Notion API, Update page  
    https://developers.notion.com/reference/patch-page
  - ProseMirror Reference, Document Structure  
    https://prosemirror.net/docs/ref/
  - Rocicorp fractional-indexing README  
    https://github.com/rocicorp/fractional-indexing
- 내부 문서
  - [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
  - [docs/decisions/008-adopt-gap-based-lexicographic-sort-key-policy.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/008-adopt-gap-based-lexicographic-sort-key-policy.md)
  - [docs/decisions/009-map-document-hierarchy-with-jpa-associations-and-db-cascade.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/009-map-document-hierarchy-with-jpa-associations-and-db-cascade.md)
  - [docs/decisions/010-map-block-hierarchy-with-jpa-associations-and-db-cascade.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/010-map-block-hierarchy-with-jpa-associations-and-db-cascade.md)

### 참고 자료와 현재 구조에서 확인한 점

- Notion 공개 자료 기준으로는 개념적으로 block 중심에 가깝더라도, API와 리소스 경계는 page와 block을 분리한다.
- 편집기 내부 `Node` 모델과 서버 영속 모델은 같은 문제가 아니다.
- 공통 정렬 정책은 엔티티 통합과 별개로 공유할 수 있다.
- 현재 코드 기준으로 move는 이미 문서와 블록 양쪽에 존재하고, `parentId`, `sortKey`, 형제 조회라는 공통 문맥을 가진다.
- transaction/save는 이름상 문서 transaction이지만, 실제 성격은 문서 컨텍스트 안의 편집 operation batch apply에 가깝다.
- transaction/save는 이미 `DocumentTransactionServiceImpl`, `DocumentTransactionOperationExecutor` 같은 orchestration 구조를 갖고 있다.
- 즉 현재 구조는 영속 통합보다 operation orchestration에 더 가까운 축이 이미 존재한다.

## 검토 범위

- `Document`, `Block`, 추후 `Workspace`까지 하나의 `Node` 영속 모델로 묶을지 검토한다.
- 영속 통합 대신 operation-level 공통화가 더 적절한지 검토한다.
- 리소스 CRUD와 editor operation의 경계를 어디에 둘지 검토한다.

다루지 않는 범위:

- 이번 문서에서 구체적인 request/response DTO 최종안까지 확정하지 않는다.
- 이번 문서에서 실제 API 구현이나 controller 코드 변경은 다루지 않는다.
- 이번 문서에서 REQUIREMENTS나 ADR 채택까지 바로 진행하지 않는다.

## 핵심 질문

1. `Document`와 `Block`의 공통성을 영속 엔티티 수준까지 끌어올리는 것이 지금 유지보수성에 실제로 도움이 되는가
2. 공통화가 필요하다면, 그 중심은 상태 모델인가 operation 입구인가
3. 리소스 CRUD와 editor operation의 경계를 어디에서 나누는 것이 현재 구조에 가장 자연스러운가

## 검토한 선택지

### 선택지 1. `Node` 단일 영속 엔티티 + 단일 테이블

개요

- `Node`에 공통 필드를 두고 `Document`, `Block`, `Workspace`를 type으로 구분한다.
- `nodes` 테이블이 중심이 된다.

시나리오

- 문서, 블록, 추후 워크스페이스 생성/이동/삭제가 모두 `nodes` 중심으로 처리된다.
- 각 타입은 공통 `Node`에 붙은 세부 속성으로 해석되거나, type 분기 기반으로 검증된다.
- move나 정렬 변경도 우선 `Node` 레벨 위치 변경으로 설명된다.

장점

- 개념적으로는 통합감이 있다.
- 장기적으로 graph-like 모델로 확장하기 쉽다.
- 모든 이동/정렬 API를 하나의 리소스로 설명하기 편하다.

단점

- null 가능 필드와 타입 분기 로직이 늘어난다.
- 문서/블록/워크스페이스의 lifecycle 차이를 공통 엔티티가 가리게 된다.
- 현재 v1에서는 `Workspace`가 활성 범위 밖이라 아직 없는 문제를 위해 중심 모델을 먼저 바꾸는 셈이 된다.
- 공통 엔티티를 만든 뒤에도 실제 검증과 세부 정책은 대부분 타입별로 갈라질 가능성이 높다.

판단

- 현재 시점에는 과하다.

### 선택지 2. 도메인/테이블은 분리 유지, 공통 policy나 facade만 추가

개요

- `Document`, `Block` 영속 모델과 리소스 컨트롤러는 유지한다.
- 공통 service나 facade를 두고 내부에서 타입별 서비스로 분기한다.

시나리오

- 프론트는 기존 문서/블록 컨트롤러를 그대로 호출한다.
- 내부 application 계층에서 공통 facade가 command를 받아 타입별 서비스로 위임한다.
- 공통 정렬 정책, command, 테스트 자산은 facade 주변에서 일부 공유한다.

장점

- 현재 코드와 비교적 잘 맞는다.
- 정렬 정책, command, 테스트 자산 같은 일부 공통화는 가능하다.
- 영속 통합 없이도 application 계층 추상화는 시도할 수 있다.

단점

- 입구가 여전히 분리되어 있으면 facade가 단순 분기 계층으로 끝날 수 있다.
- `DocumentController -> Facade -> DocumentService`, `BlockController -> Facade -> BlockService` 구조만 추가되면 유지보수 이점이 약하다.
- 공통 request shape, 공통 tracing, 공통 operation mental model이 API 표면에는 드러나지 않는다.

판단

- 안전한 중간안이지만, 공통화 이점이 가장 분명하게 드러나는 구조는 아니다.

### 선택지 3. 리소스는 분리 유지, 공통 operation만 별도 입구로 묶기

개요

- 문서/블록 CRUD는 각 리소스 컨트롤러에 둔다.
- move, transaction/save 같은 editor operation만 공통 입구에서 받는다.
- 내부에서 타입별 service 또는 기존 orchestrator로 분배한다.

시나리오

- 프론트는 리소스 CRUD와 editor operation을 다른 축으로 인식한다.
- move, transaction/save 같은 작업은 `EditorOperationController` 같은 공통 입구로 들어온다.
- 내부에서는 공통 request를 command로 바꾼 뒤 타입별 서비스나 기존 transaction orchestrator로 분배한다.

장점

- 공통 흐름의 이점을 실제 API 입구에서 살릴 수 있다.
- 공통 request/command, tracing, logging, testing 포인트를 만들 수 있다.
- 영속 통합 없이도 editor interaction 기준의 일관된 API 구조를 가질 수 있다.
- 현재 transaction/save 구조와도 잘 맞는다.

단점

- operation controller와 리소스 controller의 책임 경계를 분명히 정해야 한다.
- 범위를 무리하게 넓히면 다시 generic controller가 될 수 있다.
- 공통 operation의 범위를 먼저 합의하지 않으면 이름만 통합되고 내부는 다시 분산될 수 있다.

판단

- 현재 시점에서 가장 현실적이다.

## 비교 요약

- 공통 흐름이 많다는 문제 인식은 타당하다.
- 하지만 그 공통성을 곧바로 `Node` 영속 엔티티/단일 테이블로 연결할 필요는 없다.
- facade-only 공통화는 시도할 수 있지만, 공통화 이점이 API 표면에 가장 분명하게 드러나는 구조는 아니다.
- 현재 기준 최적안은 `Node` 엔티티를 만들지 않고, editor operation만 공통 입구에서 받는 방향이다.

## 왜 이 선을 그었는가

핵심 이유는 유지보수성이다.

- 좋은 공통화는 수정 범위를 줄이고 읽기 쉬움을 늘린다.
- 나쁜 공통화는 분기와 예외를 한곳에 몰아넣어 읽기 어렵게 만든다.

현재 `Document`와 `Block`의 공통성은 영속 상태보다 operation 문맥에서 더 선명하다.
따라서 유지보수성을 높이는 더 자연스러운 방법은 엔티티를 하나로 합치는 것보다, 공통 작업을 하나의 입구에서 받는 것이다.

---

## 현재 추천 방향

### 구조 원칙

- `Node` 영속 엔티티는 도입하지 않는다.
- `nodes` 테이블도 도입하지 않는다.
- 리소스 CRUD 전체를 하나의 컨트롤러로 합치지 않는다.
- 대신 공통성이 강한 editor operation만 별도 입구로 묶는다.

### 권장 구조

- 리소스 API
  - `DocumentController`
  - 블록 전용 CRUD 컨트롤러
- 공통 operation API
  - `EditorOperationController`

### operation 입구에 둘 후보

- move
- transaction/save
- 추후 reorder / rebalance가 생기면 그 계열
- batch apply 성격 작업

### 리소스 컨트롤러에 남길 작업

- 문서 생성/조회/수정/가시성/휴지통
- 블록 content 수정
- 문서 메타데이터 수정
- ownership/policy가 강한 CRUD

### 경계 기준

- 정적 리소스 상태 CRUD면 리소스 컨트롤러
- editor 상호작용에서 발생하는 작업이면 operation 컨트롤러

## 왜 이 추천안이 현재 더 맞는가

핵심은 지금 필요한 공통화가 "영속 상태 통합"보다 "작업 흐름 통합"에 더 가깝기 때문이다.

현재 `Document`와 `Block`이 닮아 있는 부분은 주로 아래다.

- `parentId` 기반 이동
- `sortKey` 기반 ordered sibling 정렬
- 형제 사이 삽입
- editor interaction에서 발생하는 move / transaction 흐름

즉 공통성은 "무엇을 저장하느냐"보다 "어떤 작업을 수행하느냐" 쪽에서 더 강하게 보인다.

반대로 상태 모델과 도메인 규칙은 아직 충분히 다르다.

- `Document`는 계층, 소유/가시성, 휴지통, 메타데이터가 중심이다.
- `Block`은 문서 내부 콘텐츠, block type, depth, document 소속과 parent block 검증이 중심이다.

따라서 지금 `Node` 엔티티를 도입하면 아래 부담이 따른다.

- `nodes` 테이블과 매핑 구조 설계
- `type` 기반 분기와 nullable 필드 증가
- 생성/수정/삭제 책임 재정의
- 서비스 경계와 조회 전략 재설계

즉 이건 단순 필드 공통화가 아니라 영속 모델의 중심축을 바꾸는 일에 가깝다.

반면 지금 얻고 싶은 이점은 `Node` 엔티티 없이도 대부분 달성할 수 있다.

- 공통 operation request shape
- 공통 입구
- 타입별 분배
- 공통 tracing / logging / testing 포인트

따라서 현재 더 자연스러운 선택은:

- `Node`를 영속 엔티티로 만들기보다
- operation-level concept로만 두고
- `EditorOperationController` 같은 공통 operation 입구에서 처리하는 방식이다

특히 transaction/save는 이미 리소스 CRUD보다 orchestration에 가까운 구조를 갖고 있으므로, 현재 공통화 축이 영속 엔티티보다 operation 계층에 더 가깝다는 점을 다시 확인시켜준다.

## 왜 `Node` 영속 통합은 아직 과한가

### 1. `Node` hot table 전략도 조심해야 한다

여기서 흔히 나오는 반론은 이렇다.

- "공통 `Node` 테이블이 커져도 인덱스만 잘 잡으면 조회는 빠르지 않나"

이 말은 일부 조회 패턴에 대해서는 맞을 수 있다.
하지만 hot table 문제의 핵심은 "조회 한 건이 인덱스를 타느냐"만이 아니다.

> **인덱스는 조회를 빠르게 할 수 있어도, 집중 자체를 없애지는 못한다.**

`Node`를 공통 중심 테이블로 두면 아래 작업이 한 곳으로 몰린다.

- 문서 생성/이동/삭제
- 블록 생성/이동/삭제
- 추후 다른 node 계열 리소스의 생성/이동/삭제
- 정렬 키 변경
- parent 관계 변경
- soft delete / restore

핵심은 조회 속도 하나보다, 여러 종류의 읽기와 쓰기 패턴이 같은 테이블과 같은 인덱스 집합에 집중된다는 점이다.

> **인덱스가 많아질수록 쓰기 비용도 같이 커진다.**

공통 테이블은 보통 아래 유혹을 만든다.

- 문서 조회용 인덱스
- 블록 트리 조회용 인덱스
- parent/sortKey 탐색용 인덱스
- owner/visibility/삭제 상태 조회용 인덱스

하지만 insert/update/delete는 row만 바꾸는 것이 아니라 관련 인덱스도 같이 갱신한다.

즉 인덱스가 조회 성능의 해답이 되려면, 그만큼 write cost와 index maintenance cost도 같이 감수해야 한다.

현재처럼 move, reorder, transaction/save처럼 쓰기가 적지 않은 구조에서는 이 비용을 가볍게 보기 어렵다.

> **공통 테이블은 access pattern이 서로 다른 도메인을 한데 밀어넣는다.**

`Document`와 `Block`은 공통점이 있지만, 자주 필요한 조회 축은 완전히 같지 않다.

- 문서는 소유자, 가시성, 휴지통, 계층 목록이 중요하다.
- 블록은 document 소속, parent block, depth, ordered sibling 탐색이 중요하다.

이를 한 `nodes` 테이블에 모으면:

- 모든 타입에 공통으로 좋은 인덱스는 부족하고
- 타입별로 필요한 인덱스는 계속 늘고
- 결국 큰 공통 테이블이 여러 타입의 access pattern을 동시에 떠안게 된다.

즉 한 테이블로 모아 단순화하는 것이 아니라, 서로 다른 조회/쓰기 특성을 한곳에 압축하게 된다.

> **hot table 문제는 캐시 locality와 운영 영향 범위 문제도 포함한다.**

거대한 중심 테이블은 단순 row 수 문제만이 아니다.

- 어떤 타입의 대량 쓰기가 다른 타입 조회 캐시 효율에도 영향을 줄 수 있다.
- 마이그레이션이나 인덱스 재구성이 공통 테이블 전체 영향으로 번지기 쉽다.
- 특정 타입 정책 변경이 공통 스키마 변경으로 이어질 수 있다.

즉 한 타입의 변화가 전체 node 계열 운영 비용으로 확산되기 쉽다.

> **현재 프로젝트에서 얻고 싶은 공통화 이점과도 방향이 다르다.**

현재 진짜 공통화하고 싶은 것은 아래에 더 가깝다.

- move request shape
- 공통 operation 입구
- 공통 command / tracing / testing 포인트
- editor operation mental model

이건 operation 계층에서 얻을 수 있는 이점이다.

반대로 `Node` hot table은:

- 상태 저장 구조
- 인덱스 전략
- 쓰기 병목
- 스키마 진화 비용

같은 영속 계층 부담을 먼저 키운다.

즉 공통화 이점은 operation에 있는데, 비용은 storage 중심으로 먼저 치르는 구조가 되기 쉽다.

> **그래서 "인덱스를 잘 잡으면 된다"만으로는 충분하지 않다.**

정리하면 인덱스는 특정 조회를 빠르게 만드는 도구이고, hot table 문제는 여러 타입의 읽기/쓰기/인덱스 유지/스키마 진화가 한곳에 집중되는 문제다.

따라서 `Node` 전략을 검토할 때는 아래를 함께 봐야 한다.

- 단일 쿼리 속도
- write amplification
- 인덱스 수와 유지비
- 타입별 access pattern 충돌
- 운영 변경 영향 범위

현재 `Document` / `Block`은 공통 정책은 일부 있지만 상태와 access pattern은 충분히 다르다.
반면 공통화 이점은 operation 계층에서 더 크다.
따라서 공통 `Node` hot table을 중심으로 설계하는 쪽이 아직은 과하다.

### 2. `Node` 공통 엔티티 + `Document`/`Block` FK 분리도 아직 비효율적이다

단일 `nodes` 테이블 하나로 모두 합치는 안보다 덜 과격해 보여서, 아래 같은 절충안을 떠올릴 수 있다.

- `Node` 엔티티/테이블에 공통 필드만 둔다.
- `Document`, `Block`은 각자 전용 테이블을 유지한다.
- 각 전용 엔티티가 `node_id` FK로 `Node`를 참조한다.

겉으로 보면 공통 필드만 뽑고 도메인별 상세는 분리하니 균형이 좋아 보인다.
하지만 현재 기준에서는 이 방식도 비용 대비 이점이 크지 않다.

> **공통 필드가 진짜 중심 aggregate가 되지 못한다.**

이 구조가 자연스러우려면 `Node`가 단순 공통 칼럼 묶음이 아니라, 생성/이동/삭제/조회에서 먼저 의미를 갖는 중심 aggregate여야 한다.

하지만 현재 코드와 요구사항에서는 실제 책임이 그렇게 모이지 않는다.

- `Document`는 소유자, 공개 범위, 휴지통 정책, 상위 문서 cycle 검증이 핵심이다.
- `Block`은 document 소속, parent block 검증, depth 제한, content 저장이 핵심이다.
- 실제 정책 판단도 `Node` 단에서 끝나지 않고 거의 항상 `Document` 또는 `Block`까지 내려가야 한다.

즉 `Node`를 도입해도 실제 비즈니스 판단은 하위 엔티티로 다시 내려간다.
그러면 `Node`는 중심 모델이라기보다, 대부분의 요청에서 먼저 거쳐 가는 공통 헤더에 가까워진다.

> **쓰기 흐름이 한 번에 끝나지 않고 두 단계 저장으로 늘어난다.**

현재 구조에서는 문서 생성은 `documents`, 블록 생성은 `blocks` 중심으로 읽으면 된다.

하지만 `Node` + FK 구조가 되면 생성/삭제/복구/이동의 기본 흐름이 아래처럼 늘어난다.

1. 먼저 `Node`를 생성하거나 조회한다.
2. 그다음 `Document` 또는 `Block` 전용 엔티티를 생성하거나 조회한다.
3. 둘 중 하나만 바꾸면 안 되는 invariants를 함께 맞춘다.

이건 테이블 하나가 늘어나는 수준의 문제가 아니다.

- 저장 순서
- flush 타이밍
- cascade 책임
- orphan 정리
- soft delete 동기화
- version 증가 기준

같은 쓰기 정책을 전부 다시 정의해야 한다.

특히 지금은 `Document`와 `Block`이 각각 자기 lifecycle을 비교적 직접 설명한다.
그런데 `Node`가 들어오면 무엇이 root write owner인지 흐려진다.

> **읽기 단순성이 좋아지지 않고, 오히려 항상 join 전제를 깐다.**

현재 `Document`를 읽을 때는 문서 규칙만, `Block`을 읽을 때는 블록 규칙만 보면 된다.

반면 `Node` FK 구조에서는 공통 필드가 `Node`로 빠진 순간 대부분의 읽기가 join 전제가 된다.

- 문서 목록 조회도 `Document + Node`
- 블록 트리 조회도 `Block + Node`
- 이동 검증도 anchor/parent를 볼 때 `Node`와 전용 엔티티를 함께 해석

즉 공통 필드를 재사용하겠다는 의도로 넣은 계층이, 실제로는 거의 모든 조회에 기본 join 비용과 매핑 복잡도를 추가한다.

현재처럼 `Document`, `Block`이 각각 자주 독립 조회되는 구조에서는, 이 추가 계층이 읽기 모델을 단순하게 만들기보다 오히려 더 간접화한다.

> **`Node.type`을 둬도 공통 처리 이점은 제한적이고, 본질적인 조회를 대체하지 못한다.**

이 절충안에서 자주 나오는 반론은 이렇다.

- `Node.type = DOCUMENT | BLOCK` 같은 값을 두면 어느 서비스로 갈지 빨리 분기할 수 있다.
- 공통 처리 계층에서 타입만 먼저 확인하면 추가 조회 없이 라우팅이 가능하다.
- type만 잘 두면 성능에도 큰 문제가 없을 것 같다.

이 주장은 "어느 방향으로 내려갈지"를 빠르게 정한다는 점에서는 일부 맞다.
즉 `type`은 1차 라우팅 힌트로는 쓸 수 있다.

하지만 현재 구조에서는 그 이점이 routing metadata 수준에 가깝고, 실제 도메인 조회를 대체하지는 못한다.
실제 처리에서는 결국 아래가 다시 필요하다.

- `Document`면 문서 전용 엔티티와 정책을 다시 조회
- `Block`이면 블록 전용 엔티티와 정책을 다시 조회
- move나 검증이면 parent, anchor, sibling 같은 전용 정보까지 다시 확인

즉 실제 흐름은 보통 아래처럼 된다.

1. `Node` 조회
2. `type` 확인
3. 타입별 엔티티 재조회
4. 타입별 서비스 호출

이 구조에서는 `type`이 "최종 조회를 줄여주는 정보"라기보다, "어느 쪽으로 내려갈지 먼저 알려주는 힌트"에 더 가깝다.

그런데 그 힌트를 위해 감수해야 하는 비용은 여전히 크다.

- `Node` 테이블 유지
- `type` 값 관리와 동기화
- `node_id` FK 관계 유지
- 공통 엔티티와 하위 엔티티 사이의 저장 순서와 상태 일관성 관리

즉 얻는 것은 비교적 약한 1차 분기 이점인데, 비용은 영속 모델 복잡도와 조회 흐름 간접화로 남는다.

현재 구조에서는 차라리 아래처럼 명시적으로 처리하는 편이 더 직접적이다.

- 문서 작업이면 `DocumentService`
- 블록 작업이면 `BlockService`
- 공통 operation이면 controller나 application 계층에서 명시적으로 분기

따라서 `Node.type`을 둔다고 해서 공통 처리가 크게 단순해지거나, 성능상 부담이 거의 없어진다고 보기는 어렵다.
대부분의 경우 필요한 하위 도메인 정보를 다시 읽어야 하므로, 공통화 이점은 작고 간접 계층만 늘어날 가능성이 높다.

> **version, soft delete, 계층 검증의 기준점이 애매해진다.**

이 절충안이 특히 애매해지는 지점은 "공통 필드는 `Node`에 있지만, 실제 정책은 하위 엔티티에 있다"는 모순이다.

예를 들어 아래 질문이 즉시 생긴다.

- version은 `Node`가 올릴 것인지, `Document`/`Block`이 올릴 것인지
- soft delete는 `Node.deletedAt` 하나로 충분한지, 하위 엔티티 상태도 같이 관리해야 하는지
- parent/child 관계의 진짜 기준이 `Node`인지, `Document`/`Block` 전용 관계인지
- cycle 검증과 depth 검증을 어디서 최종 보장할지

이 질문들은 구현 디테일이 아니라 모델 중심축을 어디에 둘지의 문제다.

지금 구조에서는:

- 문서 계층 검증은 `Document`
- 블록 트리 검증은 `Block`
- transaction batch orchestration은 별도 application/service

로 비교적 읽기 쉽게 나뉜다.

그런데 `Node` FK 구조를 넣는 순간, 공통성과 도메인 규칙의 경계가 더 선명해지기보다 오히려 중첩된다.

> **얻는 이점이 결국 operation-level 공통화보다 작다.**

현재 정말 줄이고 싶은 중복은 아래에 더 가깝다.

- move request shape
- 공통 operation 입구
- tracing / logging / testing 포인트
- editor batch apply mental model

이건 `Node` FK 구조를 만들지 않아도 해결 가능하다.

반대로 `Node` FK 구조를 도입해도 아래는 여전히 남는다.

- `Document`와 `Block`의 별도 검증
- 별도 서비스 분기
- 별도 응답 shape
- 별도 조회 모델

즉 영속 구조는 더 무거워지는데, 실제로 공통화하고 싶은 작업 흐름은 그만큼 단순해지지 않는다.

그래서 현재 기준에서 `Node`를 공통 영속 엔티티로 세우고 하위 엔티티를 FK로 거는 방식은 단일 테이블 통합보다는 덜 과격하지만, 여전히 상태 모델 중심축을 먼저 바꾸는 비용이 크다.
또 지금 필요한 operation 공통화를 해결하는 가장 짧은 경로도 아니다.

## 왜 CRUD 전체를 묶지 않는가

move나 transaction/save는 공통 operation으로 묶을 여지가 크지만, CRUD 전체는 다르다.

이유:

- `Document` CRUD는 메타데이터, 가시성, 휴지통, 소유 정책이 중심이다.
- `Block` CRUD는 콘텐츠, 문서 소속, 부모 블록, depth, editor tree가 중심이다.
- 즉 CRUD는 리소스 의미 자체가 다르다.

반면 move나 transaction/save는 공통 operation으로 보기 쉽다.

- move는 문서와 블록 모두 "위치 변경"이라는 공통 행위를 갖는다.
- transaction/save는 둘이 동일한 CRUD를 한다는 뜻이 아니라, 특정 리소스 단건 CRUD가 아니라 편집 작업을 적용하는 상위 orchestration이기 때문에 operation 계층에 더 잘 맞는다.

기준은 단순하다.

- 정적 리소스 상태 CRUD면 리소스 컨트롤러
- editor 상호작용에서 발생하는 작업이면 operation 컨트롤러

---

## 추천 시나리오와 예시

이 섹션은 현재 채택 문서가 아니라, "만약 operation 중심 공통 입구 방향을 채택한다면 어떤 형태가 자연스러운가"를 보기 위한 예시다.

### 예시 원칙

- `Document`, `Block` 영속 엔티티는 유지한다.
- 리소스 CRUD 컨트롤러는 유지한다.
- 공통화는 operation / command / orchestration 레벨에서만 먼저 한다.
- `Node`는 영속 엔티티가 아니라 operation-level concept로만 둔다.

### 예시 구조

```text
api
  DocumentController
  BlockController
  EditorOperationController

application
  command
    MoveNodeCommand
    EditorTransactionCommand
  service
    EditorOperationService

domain
  document
    DocumentService
    Document
  block
    BlockService
    Block
  transaction
    DocumentTransactionService
    DocumentTransactionOperationExecutor

support
  OrderedSortKeyGenerator
```

### 예시 흐름 1. move

1. 프론트는 공통 move operation 요청을 보낸다.
2. `EditorOperationController`가 공통 request를 받는다.
3. 내부 command로 변환한다.
4. `EditorOperationService`가 type을 보고 `DocumentService.move(...)` 또는 `BlockService.move(...)`로 분배한다.
5. 실제 검증과 영속 변경은 각 도메인 서비스가 담당한다.

### 예시 흐름 2. transaction/save

1. 프론트는 editor operation batch를 보낸다.
2. `EditorOperationController`가 공통 입구에서 받는다.
3. 기존 `DocumentTransactionServiceImpl`, `DocumentTransactionOperationExecutor` 같은 orchestration 구조를 호출한다.
4. 즉 이 경우 새 계층을 또 만드는 것보다, 기존 orchestrator를 operation 관점에서 재배치하는 편이 자연스럽다.

## 미해결 쟁점

1. `EditorOperationController`의 request shape를 단일 endpoint로 둘지, move / transaction으로 나눌지
2. move를 먼저 공통화할지, transaction/save까지 함께 재정리할지
3. reorder / rebalance를 사용자 API로 둘지, 운영/내부 operation으로 둘지
4. 블록 CRUD 컨트롤러를 별도로 정리할지, 현재 admin 성격 구조를 유지할지

## 다음 액션

1. 공통 operation으로 볼 작업 범위를 먼저 확정한다.
   - move
   - transaction/save
   - reorder/rebalance 후보
2. `EditorOperationController`가 받을 request shape 후보를 별도 검토한다.
3. move는 새 공통 operation service로 뺄지, transaction/save는 기존 orchestrator를 재사용할지 분리 검토한다.
4. 이 검토 결과는 이후 [`ADR 021`](https://github.com/jho951/Block-server/blob/dev/docs/decisions/021-adopt-editor-operation-controller-boundary.md)로 채택되었다.

## 관련 문서

- [docs/decisions/021-adopt-editor-operation-controller-boundary.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/021-adopt-editor-operation-controller-boundary.md)
- [docs/discussions/2026-03-27-v1-remove-workspace-from-document-flow-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-v1-remove-workspace-from-document-flow-review.md)
- [docs/roadmap/v2/workspace/workspace-reintroduction.md](https://github.com/jho951/Block-server/blob/dev/docs/roadmap/v2/workspace/workspace-reintroduction.md)
- [prompts/worklog/2026-04/2026-04-05-node-domain-abstraction-review.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-04/2026-04-05-node-domain-abstraction-review.md)
