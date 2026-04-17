# OrderedSortKeyGenerator 작업 노트

## 목적

이 문서는 `OrderedSortKeyGenerator`가 어떤 문제를 해결하는지, 어떤 입력을 받아 어떤 흐름으로 `sortKey`를 생성하는지, 그리고 각 메서드가 어떤 역할을 하는지를 설명하기 위한 로컬 작업 노트다.

대상 코드는 다음 파일이다.

- `documents-infrastructure/src/main/java/com/documents/support/OrderedSortKeyGenerator.java`

---

## 1. 이 Generator가 해결하는 문제

정렬 순서를 가진 sibling 집합이 있다고 가정한다.

예:

- 같은 부모 아래의 block 목록
- 같은 부모 아래의 document 목록

여기서 새 항목을 중간에 삽입하려면 순서를 표현할 값이 필요하다.

가장 단순한 방법은 이런 방식이다.

- 1, 2, 3, 4 순번을 저장
- 중간에 새 항목이 들어오면 뒤의 모든 항목을 다시 update

이 방식은 구현은 쉽지만, 삽입/이동이 자주 일어나는 구간에서는 update 범위가 커진다.

`OrderedSortKeyGenerator`는 그 문제를 줄이기 위해 다음 방식으로 동작한다.

- 기존 sibling 전체를 재정렬하지 않는다.
- 새 항목 1개에 대해서만 새 `sortKey`를 만든다.
- 이 `sortKey`는 문자열 오름차순 정렬만으로 순서를 판별할 수 있어야 한다.

즉 핵심 목표는 다음 한 줄이다.

> 새 항목을 원하는 위치에 끼워 넣되, 가능하면 기존 row는 건드리지 않는다.

---

## 2. 저장 형식과 계산 형식

이 generator는 내부 계산과 외부 저장 형식을 분리한다.

### 저장 형식

- 고정폭 24자리
- 대문자 base36 문자열
- 예: `000000000001000000000000`

### 계산 형식

- 내부에서는 `BigInteger`로 계산

즉 다음 순서로 동작한다.

1. 기존 `sortKey` 문자열을 읽는다.
2. `BigInteger(base36)`로 변환한다.
3. 덧셈/뺄셈/중간값 계산을 한다.
4. 결과를 다시 base36 대문자 24자리 문자열로 바꾼다.

정리하면:

- 정책/저장 포맷: base36 대문자 문자열
- 구현 계산 도구: `BigInteger`

---

## 3. 전체 동작 흐름

`OrderedSortKeyGenerator`의 진입점은 다음 메서드다.

```java
public <T> String generate(
        List<T> siblings,
        Function<T, UUID> idExtractor,
        Function<T, String> sortKeyExtractor,
        UUID afterId,
        UUID beforeId
)
```

이 메서드는 다음 순서로 동작한다.

1. `resolveContext(...)`로 삽입 위치를 해석한다.
2. 현재 요청이
   - 맨 뒤 삽입인지
   - 맨 앞 삽입인지
   - 특정 항목 뒤 삽입인지
   - 특정 두 항목 사이 삽입인지
   를 판단한다.
3. 위치에 맞는 계산 메서드를 호출한다.
   - `appendAfter(...)`
   - `prependBefore(...)`
   - `between(...)`
4. 계산 결과를 `format(...)`으로 문자열 `sortKey`로 만든다.
5. 새 항목에 그 값을 저장한다.

중요한 점:

- 기존 sibling들의 `sortKey`는 바꾸지 않는다.
- 새 항목의 `sortKey`만 계산한다.

---

## 4. 핵심 상수

코드에는 다음 상수가 있다.

### `KEY_WIDTH = 24`

- 최종 `sortKey` 문자열 길이
- 항상 24자리로 저장한다.

### `KEY_RADIX = 36`

- base36 사용
- 문자 집합은 `0-9`, `A-Z`

### `DEFAULT_STEP = 36^(KEY_WIDTH/2)`

- 기본 간격
- 처음 항목을 만들거나 맨 뒤/앞으로 붙일 때 넓은 공간을 남기기 위한 값

이 기본 간격을 두는 이유는 초반에 insert가 조금 일어나도 바로 gap이 고갈되지 않게 하기 위해서다.

---

## 5. 메서드별 설명

## 5.1 `generate(...)`

### 역할

전체 흐름 제어 메서드.

### 입력

- `siblings`: 같은 부모 아래 현재 정렬 대상 목록
- `idExtractor`: 각 항목에서 ID를 꺼내는 함수
- `sortKeyExtractor`: 각 항목에서 `sortKey`를 꺼내는 함수
- `afterId`: 이 항목 뒤에 넣고 싶을 때 사용
- `beforeId`: 이 항목 앞에 넣고 싶을 때 사용

### 출력

- 새 항목에 저장할 `sortKey`

### 분기 방식

#### 경우 1. `afterId == null && beforeId == null`

의미:

- 명시적 anchor 없이 추가
- 현재 정책상 맨 뒤 append로 해석

동작:

- sibling이 없으면 첫 키 발급
- sibling이 있으면 마지막 sibling 뒤에 붙는 키 발급

#### 경우 2. `afterId != null && beforeId != null`

의미:

- 두 항목 사이에 넣고 싶음

동작:

- 두 키 사이 중간값 계산

#### 경우 3. `afterId != null`

의미:

- 특정 항목 바로 아래(뒤)에 넣고 싶음

동작:

- 다음 sibling이 있으면 그 둘 사이 중간값 계산
- 다음 sibling이 없으면 맨 뒤 append

#### 경우 4. `beforeId != null`

의미:

- 특정 항목 바로 위(앞)에 넣고 싶음

동작:

- 이전 sibling이 있으면 그 둘 사이 중간값 계산
- 이전 sibling이 없으면 맨 앞 prepend

---

## 5.2 `resolveContext(...)`

### 역할

요청의 삽입 위치를 해석하고, 계산에 필요한 주변 항목을 찾는다.

### 하는 일

1. `afterId`, `beforeId`가 같은 값인지 검사
2. 각각 sibling 목록 안에 존재하는지 검사
3. 둘 다 있으면 실제로 인접한 gap인지 검사
4. 아래 값을 묶어서 반환
   - `after`
   - `before`
   - `previousSibling`
   - `nextSibling`

### 왜 필요한가

이 메서드가 있어야 `generate(...)`가 삽입 위치를 단순한 분기로 처리할 수 있다.

예를 들어:

- `after = A`
- `nextSibling = B`

가 나오면,

- “A 다음에 삽입”
- “B가 있으니 A와 B 사이 중간값 계산”

으로 바로 갈 수 있다.

### 잘못된 요청 예

- `afterId`가 sibling 목록에 없음
- `beforeId`가 sibling 목록에 없음
- `afterId = A`, `beforeId = C`인데 실제로 A와 C 사이에 B가 있음

이 경우 예외를 던진다.

---

## 5.3 `indexOf(...)`

### 역할

sibling 목록에서 특정 ID가 몇 번째인지 찾는다.

### 예

목록:

- A
- B
- C

결과:

- `indexOf(..., A)` -> `0`
- `indexOf(..., B)` -> `1`
- `indexOf(..., 없는 ID)` -> `-1`

이 메서드는 `resolveContext(...)`에서만 사용된다.

---

## 5.4 `appendAfter(...)`

### 역할

현재 마지막 항목 뒤에 새 항목을 붙일 때 사용할 키를 만든다.

### 동작

1. 현재 키 + `DEFAULT_STEP`
2. 범위 안이면 그대로 사용
3. 범위를 넘으면 `between(current, null)`로 fallback

### 예

현재 마지막 키:

- `000000000001000000000000`

새 키:

- `000000000002000000000000`

즉 뒤에 붙일 때는 넓은 stride를 계속 사용한다.

---

## 5.5 `prependBefore(...)`

### 역할

현재 첫 항목 앞에 새 항목을 넣을 때 사용할 키를 만든다.

### 동작

1. 현재 첫 키 - `DEFAULT_STEP`
2. 0 이상이면 그대로 사용
3. 0보다 작아지면 `between(null, current)`로 fallback

### 예

첫 항목 키:

- `000000000001000000000000`

새 키:

- `000000000000000000000000`

즉 가능한 한 앞쪽 공간을 넓게 쓴다.

---

## 5.6 `between(...)`

### 역할

두 키 사이에 들어갈 새 키를 계산한다.

### 동작

1. lower, upper 경계를 정한다.
2. `(lower + upper) / 2` 계산
3. 그 값이 실제로 사이에 있으면 사용
4. 더 이상 사이 값이 없으면 `SortKeyRebalanceRequiredException`

### 중요 포인트

이 메서드가 이 정책의 핵심이다.

“중간 삽입 시 뒤쪽 전체 재정렬” 대신, “두 값 사이 중간값 1개 계산”으로 끝낸다.

### 예

두 키:

- A = `000000000001000000000000`
- B = `000000000002000000000000`

중간값:

- `000000000001I00000000000`

이 키는 문자열 정렬에서도 A와 B 사이에 온다.

### gap 고갈 예

두 키가 이미 너무 가까움:

- `000000000000000000000000`
- `000000000000000000000001`

이 둘 사이에는 더 이상 정수 기반 중간값이 없다.

이 경우:

- `SortKeyRebalanceRequiredException`

이 예외는 서비스 계층에서 `SORT_KEY_REBALANCE_REQUIRED` 비즈니스 예외로 바뀐다.

---

## 5.7 `parse(...)`

### 역할

DB에 저장된 `sortKey` 문자열을 내부 계산용 `BigInteger`로 바꾼다.

### 예

입력:

- `000000000001000000000000`

출력:

- base36 기준의 `BigInteger`

### 왜 필요한가

문자열 정렬 정책을 유지하면서도 실제 계산은 숫자로 해야 하기 때문이다.

---

## 5.8 `format(...)`

### 역할

계산된 `BigInteger`를 저장용 `sortKey` 문자열로 변환한다.

### 동작

1. 값이 범위 안인지 확인
2. base36 문자열로 변환
3. 대문자 변환
4. 24자리로 0 padding

### 예

입력:

- 어떤 `BigInteger`

출력:

- `000000000001I00000000000`

---

## 5.9 `AnchorContext`

### 역할

삽입 위치 계산에 필요한 주변 항목을 담는 내부 구조체.

포함 값:

- `after`
- `before`
- `previousSibling`
- `nextSibling`

이 구조체 덕분에 `generate(...)`가 복잡한 index 계산을 다시 하지 않아도 된다.

---

## 5.10 `SortKeyRebalanceRequiredException`

### 역할

현재 key 공간 안에서 더 이상 새 키를 만들 수 없음을 나타낸다.

이 예외는 “지금 즉시 기존 sibling 전체를 재정렬하라”는 뜻이 아니라,

> 현재 정책 안에서 새 키를 만들 수 없으니, 별도 rebalance/reorder 작업이 필요하다

는 뜻이다.

서비스에서는 이를 `SORT_KEY_REBALANCE_REQUIRED`로 변환한다.

---

## 6. 시나리오별 플로우

예시 목록:

- A = `000000000001000000000000`
- B = `000000000002000000000000`
- C = `000000000003000000000000`

모두 같은 부모 아래 sibling이라고 가정한다.

---

## 시나리오 1. 빈 목록에 첫 항목 생성

### 입력

- `siblings = []`
- `afterId = null`
- `beforeId = null`

### 흐름

1. `generate(...)` 호출
2. `resolveContext(...)` 결과:
   - `after = null`
   - `before = null`
3. anchor가 없고 sibling도 없음
4. `format(DEFAULT_STEP)` 사용

### 결과

- 새 `sortKey = 000000000001000000000000`

---

## 시나리오 2. 맨 뒤에 새 항목 추가

### 입력

- `siblings = [A, B, C]`
- `afterId = null`
- `beforeId = null`

### 흐름

1. `generate(...)`
2. `resolveContext(...)`
   - 둘 다 null
3. 마지막 sibling = C
4. `appendAfter(parse(C.sortKey))`
5. `C + DEFAULT_STEP`
6. `format(...)`

### 결과

- C 뒤에 새 key 생성
- 기존 A, B, C는 변경 없음

---

## 시나리오 3. A 바로 아래에 삽입

### 입력

- `siblings = [A, B, C]`
- `afterId = A.id`
- `beforeId = null`

### 흐름

1. `generate(...)`
2. `resolveContext(...)`
   - `after = A`
   - `nextSibling = B`
3. 다음 sibling이 있으므로 `between(A, B)`
4. `(A + B) / 2`
5. `format(...)`

### 결과

- 새 key는 A와 B 사이 값
- 기존 A, B, C의 `sortKey`는 그대로 유지

---

## 시나리오 4. B 바로 위에 삽입

### 입력

- `siblings = [A, B, C]`
- `afterId = null`
- `beforeId = B.id`

### 흐름

1. `generate(...)`
2. `resolveContext(...)`
   - `before = B`
   - `previousSibling = A`
3. 이전 sibling이 있으므로 `between(A, B)`
4. `(A + B) / 2`

### 결과

- 새 key는 A와 B 사이 값

즉 “B 바로 위 삽입”과 “A 바로 아래 삽입”은 같은 gap을 가리키므로 같은 결과가 나온다.

---

## 시나리오 5. A와 B 사이를 명시해서 삽입

### 입력

- `siblings = [A, B, C]`
- `afterId = A.id`
- `beforeId = B.id`

### 흐름

1. `resolveContext(...)`
   - 둘 다 존재
   - 실제로 인접한 gap인지 확인
2. `between(A, B)` 호출

### 결과

- 새 key는 A와 B 사이 값

---

## 시나리오 6. 첫 항목보다 앞에 삽입

### 입력

- `siblings = [A, B, C]`
- `afterId = null`
- `beforeId = A.id`

### 흐름

1. `resolveContext(...)`
   - `before = A`
   - `previousSibling = null`
2. 맨 앞 삽입으로 해석
3. `prependBefore(A)`
4. `A - DEFAULT_STEP`

### 결과

- 첫 항목 앞의 새 key 발급

---

## 시나리오 7. 잘못된 gap 지정

### 입력

- `siblings = [A, B, C]`
- `afterId = A.id`
- `beforeId = C.id`

### 흐름

1. `resolveContext(...)`
2. `afterIndex = 0`
3. `beforeIndex = 2`
4. 인접한 gap이 아니므로 예외

### 결과

- `IllegalArgumentException`

서비스 계층에서는 `INVALID_REQUEST`

---

## 시나리오 8. gap 고갈

### 입력

- `siblings = [X, Y]`
- `X.sortKey = 000000000000000000000000`
- `Y.sortKey = 000000000000000000000001`
- `afterId = X.id`
- `beforeId = Y.id`

### 흐름

1. `between(X, Y)` 호출
2. `(0 + 1) / 2 = 0`
3. 결과가 lower와 upper 사이의 유효한 새 값이 아님
4. `SortKeyRebalanceRequiredException`

### 결과

- 새 키 생성 실패
- 후속 reorder/rebalance 필요

---

## 7. 왜 이 방식이 유리한가

### 장점

- 삽입/이동 시 기존 sibling 전체 update를 피할 수 있다.
- ordered sibling 구조라면 block, document 등 여러 도메인에서 재사용 가능하다.
- DB 정렬은 문자열 오름차순만 사용하면 된다.

### 단점

- 같은 gap에 삽입이 반복되면 언젠가 재균형이 필요하다.
- 사람이 보기엔 자연스러운 순번이 아니다.
- 운영 도구/디버깅 문서가 함께 필요하다.

---

## 8. 현재 이 generator를 어디에 쓰는가

현재는 document 생성/이동과 block 생성/이동에서 사용 중이다.

사용 위치:

- `documents-infrastructure/src/main/java/com/documents/service/DocumentServiceImpl.java`
- `documents-infrastructure/src/main/java/com/documents/service/BlockServiceImpl.java`

방식:

- sibling 목록 조회
- `OrderedSortKeyGenerator.generate(...)` 호출
- 새 block의 `sortKey` 세팅
- block 1건만 저장

향후에는 document 등 다른 ordered sibling 도메인에도 같은 방식으로 붙일 수 있다.

---

## 9. 한 줄 요약

`OrderedSortKeyGenerator`는

> 같은 부모 아래 정렬된 항목들 사이에서, 기존 항목들의 `sortKey`를 가능한 한 건드리지 않고 새 항목 1개에 대한 lexicographic gap key를 생성하는 공용 유틸

이다.
