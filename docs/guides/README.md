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
  - [docs/discussions/](https://github.com/jho951/Block-server/blob/dev/docs/discussions/README.md)
- 채택된 공식 결정
  - [docs/decisions/](https://github.com/jho951/Block-server/blob/dev/docs/decisions/README.md)
- 핵심 구조 설명 자체가 중심인 문서
  - [docs/explainers/](https://github.com/jho951/Block-server/blob/dev/docs/explainers/README.md)

## 작성 기준

- 문서는 구현자 관점에서 바로 실행 가능한 수준으로 적는다.
- "무엇을 왜 만드는가"보다 "무엇을 어떤 순서로 붙이고 무엇을 확인해야 하는가"가 중심이 되게 작성한다.
- 관련 정책이나 계약이 바뀌면 같은 작업에서 guide도 함께 갱신한다.
- 체크리스트나 순서가 있는 문서는 항목이 빠지지 않게 명시적으로 적는다.
- guide는 특히 구현 순서, 확인 기준, 예외 처리가 한 흐름으로 바로 이어지게 적는다.
