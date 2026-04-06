# Docs 운영 메모

이 디렉토리는 프로젝트 문서를 성격별로 나눠 보관한다.

문서를 새로 만들거나 갱신할 때는 먼저 어떤 성격의 문서인지 판단하고, 해당 하위 디렉토리의 `README.md`가 있으면 그 기준을 따른다.

## 디렉토리 맵

- [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)
  - 현재 유효한 제품 요구사항과 채택된 정책
- [docs/discussions/](https://github.com/jho951/Block-server/blob/dev/docs/discussions/README.md)
  - 채택 전 설계 검토, 전략 비교, 회의 메모
- [docs/decisions/](https://github.com/jho951/Block-server/blob/dev/docs/decisions/README.md)
  - 채택된 기술 결정과 ADR
- [docs/runbook/](https://github.com/jho951/Block-server/blob/dev/docs/runbook/README.md)
  - 재현 가능한 디버깅 절차와 운영 점검 메모
- [docs/roadmap/](https://github.com/jho951/Block-server/blob/dev/docs/roadmap/README.md)
  - 현재 기능 기준 후속 Todo와 버전별 확장 검토
- [docs/explainers/](https://github.com/jho951/Block-server/blob/dev/docs/explainers/README.md)
  - 코드만으로 빠르게 파악하기 어려운 핵심 기술 구조 설명
- [docs/learn/](https://github.com/jho951/Block-server/blob/dev/docs/learn/README.md)
  - 개인 학습용 로컬 문서와 개인 트러블슈팅 기록
- [docs/guides/](https://github.com/jho951/Block-server/blob/dev/docs/guides/README.md)
  - 프론트/서버 구현 계약, 작업 순서, 체크리스트

## 기본 원칙

- 같은 내용을 여러 문서에 중복으로 적기보다, 문서 성격에 맞는 위치 한 곳에 먼저 정리한다.
- 문서는 읽는 흐름이 먼저 보이게 작성한다. 문제 제기, 비교, 판단, 후속 액션이 가능한 한 자연스러운 순서로 이어지도록 구성한다.
- 한 문서 안에서 앞에서 제기한 질문은 같은 흐름 안에서 설명과 판단까지 이어서 끝낸다. 관련 없는 다른 쟁점을 끼워 넣어 문맥이 튀지 않게 한다.
- 같은 문서 안에서 같은 근거나 결론을 여러 번 반복하지 않는다. 반복이 필요하면 앞선 섹션을 짧게 요약하거나 링크로 참조한다.
- 문서 내부 링크는 로컬 절대 경로 대신 GitHub 저장소 기준 `blob/dev` 링크를 사용한다.
- 외부 공식 문서나 외부 사례를 근거로 썼다면, 문맥을 끊지 않는 선에서 본문 문장 안에 그 출처 성격이 드러나게 적는다. 사실과 그 문서의 해석이 섞일 때는 해석이라는 점도 함께 드러낸다.
- 요구사항으로 확정된 내용은 최종적으로 [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)에 반영한다.
- 아직 채택되지 않은 비교나 검토는 [docs/discussions/](https://github.com/jho951/Block-server/blob/dev/docs/discussions/README.md)에 남긴다.
- 실제로 채택된 결정만 [docs/decisions/](https://github.com/jho951/Block-server/blob/dev/docs/decisions/README.md) ADR로 승격한다.
- 디버깅/장애 재현 절차는 [docs/runbook/](https://github.com/jho951/Block-server/blob/dev/docs/runbook/README.md)에 남긴다.
- 개인 학습용 설명과 개인 트러블슈팅 기록은 [docs/learn/](https://github.com/jho951/Block-server/blob/dev/docs/learn/README.md)에 남긴다.
- 설명 문서와 구현 가이드는 [docs/explainers/](https://github.com/jho951/Block-server/blob/dev/docs/explainers/README.md), [docs/guides/](https://github.com/jho951/Block-server/blob/dev/docs/guides/README.md)의 경계를 지킨다.

## 흐름 원칙

- 문서는 섹션별 정보 목록이 아니라, 처음 읽는 사람이 앞에서 뒤로 읽으며 판단을 따라갈 수 있는 구조를 목표로 한다.
- 추천안이나 현재 판단이 있는 문서는 그 판단을 먼저 고정한 뒤, 바로 이어서 근거와 반대 선택지의 한계를 묶어 설명하는 편을 우선한다.
- 선택지 비교 문서는 선택지별 설명이 끝난 뒤 다시 같은 근거를 흩어 적지 않는다. 공통 근거는 `비교 요약` 또는 `현재 판단` 같은 한 구간에서 정리한다.
- 설명 문서는 정의만 나열하지 말고, 왜 이 개념이 필요한지와 어디에 적용되는지를 같은 흐름에서 연결한다.
- 구현 가이드는 배경 설명이 길어지기보다, 작업 순서와 확인 기준이 한 흐름으로 이어지게 작성한다.
