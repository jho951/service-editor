# 2026-03-27 Gateway 신뢰 헤더 검토 메모

## 문서 목적

- Gateway 인증 완료 후 `X-User-Id`를 신뢰 헤더로 사용하는 운영 방식을 정리한다.
- Block-server에서 누락/빈값 처리와 감사 로그 기준을 합의한다.
- 본 문서는 채택 전 검토 메모다.

## 배경

- MSA 구조에서 인증 검증 책임은 Gateway/Auth-service가 가진다.
- 서비스는 인증 완료 컨텍스트를 소비하는 내부 서비스 역할을 가진다.
- 기존 컨트롤러별 헤더 파싱은 중복이 있고, 공통 감사 로그(`userId`, `requestId`) 기준이 분산되어 있었다.

## 검토 범위

- `X-User-Id` 신뢰 경계와 서비스 검증 방식
- `X-Request-Id` 처리 방식
- 감사 로그 최소 필드

## 핵심 질문

1. 서비스에서 `X-User-Id`를 신뢰해도 되는가?
2. 누락/빈값 처리 기준을 어디에 둘 것인가?

## 선택지

### 선택지 1. 컨트롤러별 `@RequestHeader` 유지

#### 개요

- 각 컨트롤러 메서드에서 `X-User-Id`를 직접 수신한다.

#### 시나리오

1. 요청이 컨트롤러에 도달하면 메서드별로 헤더를 파싱한다.
2. 누락은 프레임워크 예외로 처리한다.
3. 빈값 처리와 감사 로그는 메서드/서비스마다 다르게 구현될 수 있다.

#### 장점

- 구현 변경 범위가 작다.

#### 단점

- 검증/로그 정책이 분산된다.
- 빈값 처리 기준이 일관되지 않다.

### 선택지 2. 공통 인터셉터 + 인자 리졸버

#### 개요

- 인터셉터가 `/v1/**`의 `X-User-Id`와 `X-Request-Id`를 공통 처리한다.
- 컨트롤러는 `@CurrentUserId`로 인증 컨텍스트를 전달받는다.

#### 시나리오

1. Gateway가 외부 `X-User-Id`를 제거하고 인증 성공 시 재주입한다.
2. Block-server 인터셉터가 `X-User-Id` 누락/빈값을 즉시 `401`로 차단한다.
3. `X-Request-Id`가 없으면 서버에서 생성하고 응답 헤더에 반환한다.
4. 요청 종료 시 `userId + requestId`를 포함한 감사 로그를 남긴다.

#### 장점

- 인증 컨텍스트 검증과 감사 로그 정책을 중앙화할 수 있다.
- 컨트롤러 책임을 단순화할 수 있다.

#### 단점

- 인터셉터 정책에 대한 운영 의존성이 증가한다.

## 비교 요약

- 선택지 1은 빠르지만 정책 일관성이 약하다.
- 선택지 2는 구현 변경이 있지만 운영 규칙 강제와 추적성에서 유리하다.

## 현재 추천 방향

- 선택지 2를 채택한다.
- 이유:
  - Gateway 신뢰 경계와 서비스 검증 책임이 분리된다.
  - `401` 기준과 감사 로그 필드를 공통 계층에서 강제할 수 있다.
  - 컨트롤러는 인증 컨텍스트 전달에만 집중할 수 있다.

## 미해결 쟁점

1. Gateway -> Block-server 네트워크 차단 정책은 인프라 IaC 저장소에서 별도 검증 필요

## 다음 액션

1. 인터셉터/인자 리졸버 구현
2. REQUIREMENTS와 ADR 반영
3. 배포 환경에서 Gateway 경유 강제 여부 점검

## 관련 문서

- [015-trust-gateway-injected-user-header-and-enforce-request-audit.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/015-trust-gateway-injected-user-header-and-enforce-request-audit.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
