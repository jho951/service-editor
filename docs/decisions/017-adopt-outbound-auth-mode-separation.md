# ADR 017: Outbound 호출 인증 모드를 사용자 위임과 서비스 토큰으로 분리한다

## 상태

채택됨

## 배경

- 이 서비스는 inbound 인증에서 Gateway/Auth-service 검증 결과를 신뢰한다.
- 그러나 outbound(user-server 등) 호출에서는 인증 헤더 생성 규칙이 명시적으로 고정되어 있지 않았다.

## 결정

- outbound 인증 헤더 생성은 공통 컴포넌트(`OutboundAuthHeaderFactory`)에서 처리한다.
- 호출 모드는 `USER_DELEGATION`, `SERVICE_TO_SERVICE` 두 가지로 분리한다.
- `USER_DELEGATION` 모드는 inbound `Authorization: Bearer <user token>`을 전파한다.
- `SERVICE_TO_SERVICE` 모드는 사용자 토큰과 분리된 서비스용 내부 토큰(`auth.service-token.bearer-token`)을 사용한다.
- 두 모드 모두 요청 추적을 위해 `X-Request-Id` 전달을 지원한다.

## 영향

- 장점:
  - 사용자 위임 호출과 시스템 내부 호출의 보안 경계가 명확해진다.
  - outbound 인증 헤더 정책을 코드에서 일관되게 강제할 수 있다.
  - 후속 user-server 연동 시 호출 정책을 재사용할 수 있다.
- 단점:
  - 서비스 토큰 운영(발급/회전/보관) 절차를 별도로 관리해야 한다.
  - 호출부에서 모드 선택을 잘못하면 정책 위반이 생길 수 있다.

## 관련 문서

- [2026-03-27-outbound-auth-mode-review.md](https://github.com/jho951/Block-server/blob/dev/docs/discussions/2026-03-27-outbound-auth-mode-review.md)
- [REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
