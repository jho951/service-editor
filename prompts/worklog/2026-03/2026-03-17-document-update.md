# 2026-03-17 Document Update

## Step 1. API 스켈레톤 추가

- 목적: 문서 수정 API의 PATCH 엔드포인트 뼈대를 먼저 연결한다.
- 변경 내용: `documents-api` 요청 DTO와 컨트롤러, `documents-core` 서비스 인터페이스, `documents-infrastructure` 구현 시그니처에 `PATCH /v1/documents/{documentId}`와 `update(...)` 계약을 추가했다.
- 검증: WebMvc 테스트 기준으로 응답 엔벨로프와 라우팅 확인 계획을 잡았다.

## Step 2. 서비스 로직 구현

- 목적: 문서 수정 규칙을 기존 요구사항 범위 안에서 실제 코드에 반영한다.
- 변경 내용: `DocumentServiceImpl#update`에 제목 trim/빈 문자열 검증, 부모 문서 검증, 자기 자신 부모 금지, 워크스페이스 일치 확인, 순환 참조 차단, `updatedBy` 갱신 로직을 추가했다.
- 변경 내용: 빈 제목 검증 실패가 `VALIDATION_ERROR`로 응답되도록 전역 예외 매핑을 보강했다.
- 요구사항 변경: 없음.

## Step 3. 서비스 단위 테스트 보강

- 목적: 문서 수정 핵심 규칙을 빠르게 검증하는 서비스 테스트를 고정한다.
- 변경 내용: `DocumentServiceImplTest`에 title만 수정, 루트 이동, 부모 없음, 다른 workspace 부모, 자기 자신 부모, 순환 참조, actorId 공백 처리 케이스를 추가했다.
- 검증 범위: `DocumentServiceImpl#update`의 필드 갱신과 부모/순환/감사 필드 검증.

## Step 4. WebMvc 테스트 보강

- 목적: HTTP 요청-응답 매핑과 오류 응답 코드를 먼저 고정한다.
- 변경 내용: `DocumentControllerWebMvcTest`에 수정 성공, 문서 없음, 자기 자신 부모 지정, 다른 워크스페이스 부모 지정, 공백 제목, 인증 헤더 누락 케이스를 추가했다.
- 검증 범위: `PATCH /v1/documents/{documentId}`의 요청 매핑, 응답 엔벨로프, 예외 코드 변환.

## Step 5. 통합 테스트 보강

- 목적: 실제 HTTP -> Controller -> Service -> JPA 경로에서 수정 계약이 유지되는지 확인한다.
- 변경 내용: `DocumentApiIntegrationTest`에 문서 없음 404, 다른 workspace 부모 지정 400, 자기 자신 부모 지정 400 케이스를 추가했다.
- 검증 내용: 기존 수정 성공 테스트는 PATCH 성공 시 응답과 DB 실제 변경을 함께 검증하도록 유지했다.

## Step 6. 가독성 리팩터링 재정리

- 목적: 동작을 바꾸지 않고 `DocumentServiceImpl#update` 흐름을 다시 읽기 쉽게 정리한다.
- 변경 내용: 부모 검증, 제목 적용, 메타 적용을 private 메서드로 분리해 메서드 구조를 단순화했다.
- 동작 범위: 검증 순서와 예외/저장 동작은 유지하고 메서드 구조만 정리했다.
- 테스트 확인: 기존 `DocumentServiceImplTest`로 회귀 확인.

## Step 7. 서비스 중복 검증 제거

- 목적: 요청 DTO가 이미 보장하는 제목 검증과 서비스 내부 검증이 중복되지 않도록 `DocumentServiceImpl#update` 흐름을 단순화한다.
- 변경 내용: `DocumentServiceImpl#applyTitle`에서 빈 문자열 재검증과 `VALIDATION_ERROR` 분기를 제거하고, 요청단 검증 이후에는 정규화 결과만 적용하도록 정리했다.
- 테스트 변경: `DocumentServiceImplTest`에서 DTO가 대신 보장하는 공백 제목 서비스 검증 케이스를 제거했다.
- 요구사항 변경: 없음. 요청단 검증과 서비스 책임 분리 정리다.

## Step 8. 필수 title 계약에 맞춘 테스트 재정렬

- 목적: `UpdateDocumentRequest.title`이 `@NotBlank`인 현재 계약과 문서 수정 테스트를 일치시킨다.
- 테스트 변경: parent 검증 관련 PATCH 테스트들이 비즈니스 검증까지 도달하도록 `title` 필드를 명시적으로 추가했다.
- 테스트 변경: WebMvc 테스트와 통합 테스트에 `title` 누락 시 유효성 검사 실패 케이스를 추가했다.
- 요구사항 변경: 없음. 현재 요청 DTO 제약을 테스트에 반영했다.
