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
- 문서 내부 링크는 로컬 절대 경로 대신 GitHub 저장소 기준 `blob/dev` 링크를 사용한다.
- 요구사항으로 확정된 내용은 최종적으로 [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)에 반영한다.
- 아직 채택되지 않은 비교나 검토는 [docs/discussions/](https://github.com/jho951/Block-server/blob/dev/docs/discussions/README.md)에 남긴다.
- 실제로 채택된 결정만 [docs/decisions/](https://github.com/jho951/Block-server/blob/dev/docs/decisions/README.md) ADR로 승격한다.
- 디버깅/장애 재현 절차는 [docs/runbook/](https://github.com/jho951/Block-server/blob/dev/docs/runbook/README.md)에 남긴다.
- 개인 학습용 설명과 개인 트러블슈팅 기록은 [docs/learn/](https://github.com/jho951/Block-server/blob/dev/docs/learn/README.md)에 남긴다.
- 설명 문서와 구현 가이드는 [docs/explainers/](https://github.com/jho951/Block-server/blob/dev/docs/explainers/README.md), [docs/guides/](https://github.com/jho951/Block-server/blob/dev/docs/guides/README.md)의 경계를 지킨다.
