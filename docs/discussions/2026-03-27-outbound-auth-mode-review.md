# 2026-03-27 Outbound 인증 모드 분리 검토 메모

## 문서 목적

- block-server가 다른 내부 서비스(user-server 등)를 호출할 때 인증 헤더 처리 기준을 정리한다.
- 사용자 위임 호출과 시스템 내부 호출의 토큰 정책을 분리해야 하는 이유를 검토한다.
- 이 문서는 채택 전 전략 비교 메모다.

## 배경

- 현재 서비스는 inbound 요청에서 Gateway 주입 `X-User-Id`를 신뢰한다.
- outbound 호출에 대해서는 `Authorization` 전파 기준과 서비스 토큰 기준이 코드로 고정되어 있지 않다.

## 검토 범위

- 사용자 위임 모드와 서비스 간 호출 모드의 인증 헤더 생성 정책
- 공통 헤더 생성 컴포넌트 책임 범위

## 핵심 질문

1. user-server 호출 시 언제 사용자 토큰을 그대로 전달해야 하는가
2. 시스템 내부 호출에 사용자 토큰을 쓰지 않고 별도 토큰을 써야 하는가

## 고려한 자료와 사례

- `docs/REQUIREMENTS.md`
- `docs/decisions/015-trust-gateway-injected-user-header-and-enforce-request-audit.md`
- `documents-api` 인증 인터셉터 및 인자 리졸버 구현

## 선택지

### 선택지 1. 단일 토큰 정책

#### 개요

- outbound 호출에서 항상 inbound `Authorization`을 그대로 전파한다.

#### 시나리오

1. 클라이언트 요청이 gateway를 거쳐 block-server로 들어온다.
2. block-server는 user-server 호출 시 inbound 토큰을 그대로 붙인다.
3. 내부 시스템 작업 호출도 같은 사용자 토큰을 재사용한다.

#### 장점

- 구현이 단순하다.
- 초기 개발 속도가 빠르다.

#### 단점

- 시스템 내부 호출 책임과 사용자 위임 호출 책임이 섞인다.
- 사용자 토큰이 불필요한 경로까지 확산된다.

### 선택지 2. 모드 기반 분리 정책

#### 개요

- outbound 호출을 `USER_DELEGATION`과 `SERVICE_TO_SERVICE` 두 모드로 분리한다.

#### 시나리오

1. 사용자 권한 위임이 필요한 호출이면 `Authorization: Bearer <user token>`을 전달한다.
2. 시스템 내부 호출이면 설정된 서비스용 내부 토큰을 사용한다.
3. 공통 팩토리가 `X-Request-Id`와 필요한 신뢰 헤더를 함께 구성한다.

#### 장점

- 사용자 위임과 시스템 호출 경계가 명확하다.
- 토큰 오용 가능성을 줄일 수 있다.
- 호출 정책을 코드에서 일관되게 강제할 수 있다.

#### 단점

- 모드 선택 실수를 막기 위한 코드 리뷰/테스트가 필요하다.
- 서비스 토큰 운영(회전/배포) 절차를 같이 가져가야 한다.

## 비교 요약

- 선택지 1은 단순하지만 보안 경계가 약하다.
- 선택지 2는 초기 구현이 조금 늘지만 장기 운영에서 정책 일관성이 높다.

## 추천 시나리오

- 사용자 문맥이 필요한 권한 확인 API 호출은 `USER_DELEGATION`을 사용한다.
- 시스템 스케줄러/백그라운드 동기화 호출은 `SERVICE_TO_SERVICE`를 사용한다.

## 현재 추천 방향

- 선택지 2 채택
- outbound 헤더 생성 공통 컴포넌트에 모드 기반 정책을 고정한다.

## 미해결 쟁점

1. 서비스 토큰 회전 주기와 비밀 저장소 연동 방식
2. user-server에서 요구하는 추가 신뢰 헤더 범위

## 다음 액션

1. outbound 인증 모드 enum 및 헤더 팩토리 구현
2. 서비스 토큰 설정 키 추가
3. 채택 내용 ADR 승격 및 REQUIREMENTS 반영

## 관련 문서

- [017-adopt-outbound-auth-mode-separation.md](https://github.com/jho951/Block-server/blob/dev/docs/decisions/017-adopt-outbound-auth-mode-separation.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
- [2026-03-27-outbound-auth-mode-separation.md](https://github.com/jho951/Block-server/blob/dev/prompts/worklog/2026-03/2026-03-27-outbound-auth-mode-separation.md)
