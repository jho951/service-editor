# Docs 운영 메모

이 디렉토리는 프로젝트 문서를 성격별로 나눠 보관한다.

문서를 새로 만들거나 갱신할 때는 먼저 어떤 성격의 문서인지 판단하고, 해당 하위 디렉토리의 `README.md`가 있으면 그 기준을 따른다.

이 파일은 `docs/` 전체에 공통으로 적용되는 전역 문서 규칙을 관리한다. 하위 `README.md`는 이 기준을 전제로 하고, 각 디렉토리에서만 필요한 추가 규칙만 다룬다.

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
- 같은 문서 안에서 `배경`의 참고 문서와 `관련 문서`의 링크 역할이 겹치지 않게 한다. `배경`에는 직접 판단 근거로 쓴 최소 세트만 두고, `관련 문서`에는 읽은 뒤 따라갈 후속 탐색 링크를 둔다.
- 같은 내부 문서를 `배경`과 `관련 문서` 두 곳에 반복하지 않는다. 꼭 필요하면 한쪽에만 두고 다른 쪽에서는 문장으로만 참조한다.
- 요구사항으로 확정된 내용은 최종적으로 [docs/REQUIREMENTS.md](https://github.com/jho951/Block-server/blob/dev/docs/REQUIREMENTS.md)에 반영한다.
- 아직 채택되지 않은 비교나 검토는 [docs/discussions/](https://github.com/jho951/Block-server/blob/dev/docs/discussions/README.md)에 남긴다.
- 실제로 채택된 결정만 [docs/decisions/](https://github.com/jho951/Block-server/blob/dev/docs/decisions/README.md) ADR로 승격한다.
- 디버깅/장애 재현 절차는 [docs/runbook/](https://github.com/jho951/Block-server/blob/dev/docs/runbook/README.md)에 남긴다.
- 개인 학습용 설명과 개인 트러블슈팅 기록은 [docs/learn/](https://github.com/jho951/Block-server/blob/dev/docs/learn/README.md)에 남긴다.
- 설명 문서와 구현 가이드는 [docs/explainers/](https://github.com/jho951/Block-server/blob/dev/docs/explainers/README.md), [docs/guides/](https://github.com/jho951/Block-server/blob/dev/docs/guides/README.md)의 경계를 지킨다.
- `docs/learn/` 내용은 로컬 학습 메모로 취급한다. 해당 내용이 공식 경로로 다시 정리되지 않았다면, `docs/`의 공식 문서 근거나 직접 링크 대상으로 사용하지 않는다.

## 흐름 원칙

- 문서는 섹션별 정보 목록이 아니라, 처음 읽는 사람이 앞에서 뒤로 읽으며 판단을 따라갈 수 있는 구조를 목표로 한다.
- 추천안이나 현재 판단이 있는 문서는 그 판단을 먼저 고정한 뒤, 바로 이어서 근거와 반대 선택지의 한계를 묶어 설명하는 편을 우선한다.
- 선택지 비교 문서는 선택지별 설명이 끝난 뒤 다시 같은 근거를 흩어 적지 않는다. 공통 근거는 `비교 요약` 또는 `현재 판단` 같은 한 구간에서 정리한다.
- 설명 문서는 정의만 나열하지 말고, 왜 이 개념이 필요한지와 어디에 적용되는지를 같은 흐름에서 연결한다.
- 구현 가이드는 배경 설명이 길어지기보다, 작업 순서와 확인 기준이 한 흐름으로 이어지게 작성한다.
- README끼리도 같은 공통 규칙을 반복하지 않는다. 전역 원칙은 가능한 한 `docs/README.md` 한 곳에 두고, 하위 README에는 해당 디렉토리 전용 추가 기준만 남긴다.
- 문서 규칙이나 템플릿을 수정한 뒤에는, 전역 규칙이 하위 README나 템플릿에 다시 복제되지 않았는지 확인한다.
- 문서 규칙이나 템플릿을 수정한 뒤에는, 같은 규칙이 한 파일 안의 여러 섹션에 중복으로 남지 않았는지 확인한다.

## Markdown 가독성 규칙

- 헤더 깊이는 일관되게 사용한다. `##`는 주요 섹션, `###`는 그 하위 논점으로 사용하고, `####` 이하는 꼭 필요한 경우에만 쓴다.
- 들여쓰기로 문단 구조를 만들지 않는다. 가독성은 헤더, 짧은 문단, 빈 줄, 일관된 리스트 형식으로 확보한다.
- 같은 성격의 항목은 같은 리스트 형식을 유지한다. 원칙/포인트는 `-`, 순서/절차는 `1.` 형식을 우선한다.
- 한 문단에는 하나의 주장이나 설명만 담고, 길어지면 문단을 나눈다.
- 강조는 최소한으로 사용한다. 식별자, 코드, 경로, API 이름은 백틱으로 표기하고, 굵은 글씨는 정말 핵심어에만 제한한다.
- 표는 비교 항목이 매우 정형적일 때만 쓰고, 일반 설명은 리스트나 짧은 문단을 우선한다.
- `###` 아래에서 세부 논점을 여러 개 구분해야 하는데 `####`가 과하거나 구분감이 약하면, `> **핵심 문장**` 형식의 짧은 리드 문장을 두고 바로 설명과 리스트를 잇는 방식을 사용할 수 있다.
- 구분선 `---`은 남발하지 않는다. 헤더와 빈 줄만으로도 흐름이 충분하면 굳이 넣지 않는다.
- 구분선 `---`은 가독성을 해치지 않는 선에서, 같은 문서 안에서 읽는 모드가 크게 바뀌거나 흐름 전환이 약해 보이는 지점에 제한적으로 사용할 수 있다.
  - 예: 선택지 비교에서 현재 추천 방향으로 넘어갈 때
  - 예: 본문 설명에서 추천 시나리오, 예시, 부록 성격 구간으로 넘어갈 때
- 같은 `##` 레벨이라고 해서 일괄적으로 넣지 않는다. 일반적인 섹션 전환은 헤더와 빈 줄만으로 처리한다.
