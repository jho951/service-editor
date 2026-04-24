# Guides 운영 메모

이 디렉토리는 구현자가 실제로 무엇을 만들고, 어떤 순서로 붙이고, 무엇을 확인해야 하는지 이해하도록 돕는 문서를 둔다.

## 무엇을 여기에 두는가

- 프론트/서버 구현 계약
- 요청/응답 형식 연결 기준
- 구현 순서
- 체크리스트
- 역할 분담 기준
- 협업 시 맞춰야 하는 처리 규칙

## 무엇을 여기에 두지 않는가

- 채택 전 전략 비교
  - [docs/discussions/](https://github.com/jho951/editor-service/blob/main/docs/discussions/README.md)
- 채택된 공식 결정
  - [docs/decisions/](https://github.com/jho951/editor-service/blob/main/docs/decisions/README.md)
- 핵심 구조 설명 자체가 중심인 문서
  - [docs/explainers/](https://github.com/jho951/editor-service/blob/main/docs/explainers/README.md)

- [contract/contract-change-guideline.md](./contract/contract-change-guideline.md): 구현 변경 후 contract 원본과 service lock을 맞추는 절차

## 작성 기준

- 문서는 구현자 관점에서 바로 실행 가능한 수준으로 적는다.
- "무엇을 왜 만드는가"보다 "무엇을 어떤 순서로 붙이고 무엇을 확인해야 하는가"가 중심이 되게 작성한다.
- 관련 정책이나 계약이 바뀌면 같은 작업에서 guide도 함께 갱신한다.
- 체크리스트나 순서가 있는 문서는 항목이 빠지지 않게 명시적으로 적는다.
- guide는 특히 구현 순서, 확인 기준, 예외 처리가 한 흐름으로 바로 이어지게 적는다.
- 프론트/백엔드가 같은 기능을 구현할 때는 가능하면 관련 guide를 함께 갱신하고 서로 링크를 건다.
- 특정 기능의 공통 계약을 프론트/백엔드가 같이 봐야 하면, 역할 중립적인 별도 guideline 문서를 둘 수 있다.
- 공통 guideline에는 프론트/백엔드가 같이 알아야 하는 endpoint, DTO, 상태 전이, 실패 계약을 둔다.
- frontend/backend guideline은 각 역할의 구현자가 우선적으로 알아야 하는 세부 처리 기준을 적는다.
- 역할별 guideline에서 공통 계약이 필요하면, 해당 내용은 공통 guideline을 참조하고 같은 설명을 다시 복사하지 않는다.

## 구조 규칙

- guide는 개별 operation 파일을 루트에 바로 추가하기보다, 먼저 기능군/도메인 디렉토리로 묶는다.
  - 예: `docs/guides/editor/`
  - 예: `docs/guides/upload/`
- 디렉토리 이름은 가능한 한 개별 operation 이름보다 상위의 기능군/도메인 이름을 사용한다.
- 기능군/도메인 디렉토리 안에서는 아래 3문서를 기본 세트로 둔다.
  - `{feature}-guideline.md`
  - `frontend-{feature}-guideline.md`
  - `backend-{feature}-guideline.md`

## 파일 역할 규칙

- `{feature}-guideline.md`
  - 프론트/백엔드가 같이 봐야 하는 공통 계약
  - endpoint 경계
  - request/response DTO 초안
  - 상태 전이
  - 실패 처리 기준
  - 기능군 안의 operation 구조와 문서 확장 기준
- `frontend-{feature}-guideline.md`
  - 프론트 구현자가 신경 써야 하는 상태, 이벤트, 호출 시점, 실패 복구, UI 반영 기준
- `backend-{feature}-guideline.md`
  - 백엔드 구현자가 신경 써야 하는 controller, mapper, service, validation, transaction 처리 기준

## 금지 규칙

- 기능군/도메인 디렉토리 아래에 별도 `README.md`를 만들지 않는다.
- 디렉토리 구조 설명, 문서 역할 설명, 확장 기준은 `{feature}-guideline.md`에 넣는다.
- 같은 기능군 안의 공통 설명을 `{feature}-guideline.md`, `frontend-{feature}-guideline.md`, `backend-{feature}-guideline.md`에 중복 복사하지 않는다.
- 링크 정리만을 위해 guide 경로를 자주 바꾸지 않는다. 먼저 기존 구조 안에서 문서를 확장한다.

## 새 Guide 추가 순서

1. 이 기능이 `docs/guides/`에 맞는 구현 계약 문서인지 먼저 판단한다.
2. 맞다면 개별 operation명이 아니라 기능군/도메인 디렉토리 이름을 먼저 정한다.
3. 해당 디렉토리 아래에 `{feature}-guideline.md`, `frontend-{feature}-guideline.md`, `backend-{feature}-guideline.md`를 기본으로 둔다.
4. 공통 계약은 `{feature}-guideline.md`에 먼저 정리한다.
5. 프론트와 백엔드 세부는 각 역할 guideline으로 나눈다.
6. 기능군 전용 확장 규칙은 해당 `{feature}-guideline.md`에 적는다.
