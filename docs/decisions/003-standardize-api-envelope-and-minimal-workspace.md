# ADR 003: 공통 API 응답 포맷을 유지하고 최소 Workspace를 선행 도입

## 상태

채택됨

## 배경

문서(Document) 기능은 `workspaceId`를 전제로 하지만, 현재 저장소에는 Workspace 모델과 API가 없다.
동시에 API 응답 포맷과 예외 처리 방식은 `documents-api` 모듈에 이미 공통 클래스로 존재하므로, 이후 기능 확장 시 매번 다른 응답 형태를 도입하면 계층 간 계약이 흔들리게 된다.

## 결정

- Document 기능의 선행 리소스로 최소 Workspace aggregate를 도입한다.
- v1의 Workspace 범위는 생성과 단건 조회로 제한한다.
- Workspace 구현은 `documents-core`의 서비스 계약, `documents-infrastructure`의 JPA 저장소/구현, `documents-api`의 DTO/Controller로 분리한다.
- 모든 API는 기존 `GlobalResponse`, `SuccessCode`, `ErrorCode`, `GlobalException`, `GlobalExceptionHandler`를 공통 계약으로 사용한다.
- 이후 Document/Block 기능도 같은 응답 포맷과 예외 처리 흐름을 유지한다.

## 영향

- 장점:
  - Document 기능이 Workspace 존재 검증에 사용할 최소 선행 리소스를 확보한다.
  - 응답 포맷과 예외 처리 정책이 고정되어 후속 API 구현의 일관성이 높아진다.
  - core/api/infrastructure 분리가 유지되어 Workspace 확장 시 기존 계층을 직접 수정하지 않고 확장하기 쉽다.
- 단점:
  - 멤버십, 권한, 삭제 정책이 없는 축소된 Workspace 모델이라 추후 보강이 필요하다.
  - 인증 연동 전까지는 감사 필드 일부가 비어 있을 수 있다.
