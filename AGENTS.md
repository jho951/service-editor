# AGENTS 가이드

- 작업 성격에 맞게 `docs/`, `prompts/`를 함께 갱신한다.
- `docs/` 하위 문서를 다룰 때는 먼저 [docs/README.md](https://github.com/jho951/Block-server/blob/dev/docs/README.md)를 읽고, 해당 디렉토리에 `README.md`가 있으면 그 기준을 따른다.
- 현재 유효한 제품 요구사항과 채택된 정책은 `docs/REQUIREMENTS.md`에 반영한다.
- 채택 전 전략 검토, 회의 메모, 비교 문서는 `docs/discussions/`에 남기고, 작성 기준은 `docs/discussions/README.md`를 따른다.
- 채택된 기술 결정과 되돌리기 어려운 정책 변경은 `docs/decisions/` ADR로 기록하고, 운영 기준은 `docs/decisions/README.md`를 따른다.
- 재현 가능한 디버깅 절차와 장애 재현 방법은 `docs/runbook/`에 남기고, 운영 기준은 `docs/runbook/README.md`를 따른다.
- 기능별 후속 Todo, 버전별 확장 검토, 열어둘 질문은 `docs/roadmap/`에 정리하고, 운영 기준은 `docs/roadmap/README.md`를 따른다.
- 코드만 보고 빠르게 파악하기 어려운 핵심 기술 구조와 내부 동작 설명은 `docs/explainers/`에 정리하고, 운영 기준은 `docs/explainers/README.md`를 따른다.
- `docs/learn/`는 개인 학습용 로컬 문서 영역이며, 운영 기준은 `docs/learn/README.md`를 따른다.
- 문제 원인 분석, 비교 검토, 채택 이유, 결과 정리처럼 트러블슈팅 기록은 `docs/learn/troubleshooting/`에 남기고, 작성 기준은 `docs/learn/troubleshooting/README.md`를 따른다.
- 프론트/서버 구현 계약, 작업 순서, 체크리스트, 역할 분담 기준은 `docs/guides/`에 정리하고, 운영 기준은 `docs/guides/README.md`를 따른다.
- 개인 이해를 위한 설명 요청은 `learn`으로, 팀 선택 검토는 `docs/discussions/`로, 실제 채택된 내용은 `docs/decisions/` ADR로 승격한다.
- `prompts/`는 팀 또는 프로젝트 단위의 의미 있는 작업 기록이 필요할 때만 남긴다. 개인 학습용 설명 요청, 간단한 질의응답, 잠깐 확인하고 끝나는 요청은 남기지 않는다.
- `prompts/`를 다룰 때는 먼저 `prompts/README.md`를 읽고 그 기준을 따른다.
- 문서 내부 링크는 로컬 절대 경로 대신 GitHub 저장소 기준 `https://github.com/jho951/Block-server/blob/dev/...` 링크를 사용한다.
- 중요한 기술 선택과 되돌리기 어려운 정책 변경은 반드시 ADR 또는 관련 문서까지 함께 반영한다.


# AGENTS Working Agreement

## Purpose

이 문서는 이 저장소에서 사람과 AI 에이전트가 협업할 때의 규칙과 작업 절차를 정의한다.

### Goal

- AI가 프로젝트의 목표, 아키텍처, 코딩 스타일을 일관되게 이해하도록 한다.
- 모든 변경 사항이 추적 가능하고 재현 가능하도록 기록한다.
- AI 작업이 코드베이스를 예측 가능한 방식으로 수정하도록 제한한다.

## Ground Rules

- 기존 제품 코드(`app/`, `components/`, `libs/` 등)의 동작을 바꾸는 변경은 목적, 근거, 영향 범위를 PR에 꼭 명시한다.
- 요구사항/가정이 바뀌면 어떤 이유로 바뀌었는지 설명하고 `docs/REQUIREMENTS.md` 갱신 여부를 묻고, 수락 시 `docs/REQUIREMENTS.md`를 함께 갱신한다.
- 채택 전 설계 검토, 전략 비교, 회의 메모는 `docs/discussions/`에 남기고, 실제 채택된 결정만 `docs/decisions/` ADR로 승격한다.
- 중요한 기술적 결정(트레이드오프, 정책 변경, 되돌리기 어려운 선택)은 ADR로 남긴다.
- 코드 주석은 꼭 필요한 경우에만 추가하고, 기본 문체는 명사형/단답형으로 유지한다.
- 에러 메시지와 예외 메시지는 특별한 사유가 없으면 기본적으로 한글로 작성한다.
- 전체 코드베이스에서 가독성(명시성)과 성능 사이에 큰 차이가 없다면 반드시 가독성(명시성)을 우선한다.
- 코드는 현재 담당자가 아닌 다른 사람이 처음 읽어도 한 번에 흐름이 보이도록 작성한다. 특히 Service, UseCase, Controller, Validator, Mapper처럼 비즈니스 흐름을 읽는 계층에서는 이 원칙을 더 강하게 적용한다.
- 메서드 분리는 역할 경계가 실제로 더 선명해질 때만 적용한다. 성능 차이가 거의 없고 흐름 파악만 어려워진다면 과도한 helper/overload 분할보다 한 번에 읽히는 구현을 우선한다.
- `List`, `Map` 같은 컬렉션을 생성해서 다른 메서드에 넘기기만 하는 wrapper/helper는 만들지 않는다. 수집 메서드가 필요하면 그 메서드 안에서 직접 결과 컬렉션을 만들고 반환한다.
- 단순 값 정리, 한 줄 위임, 컬렉션 생성, 한 번만 호출되는 얇은 포장 메서드처럼 의미를 늘리지 않는 helper는 만들지 않는다.
- 호출부만 봐도 무엇을 검증하는지, 무엇을 조회하는지, 무엇을 수집하는지, 무엇을 변경하는지 바로 읽혀야 한다. 메서드명은 축약보다 역할과 결과가 드러나는 쪽을 우선한다.
- 조건문만 봐서는 의도를 바로 알기 어려우면 인라인으로 두지 않는다. 검증, 정책 분기, 예외 발생 조건은 `validate...`, `ensure...`, `is...`, `has...`, `can...`처럼 역할이 드러나는 메서드로 감싸 호출부에서 의도가 읽히게 만든다.
- 단순 수집 로직을 위해 순회 방식 자체를 불필요하게 바꾸지 않는다. 기존 재귀가 충분히 읽기 쉬우면 stack, queue, 임시 상태 객체 같은 구조를 새로 도입하지 않는다.
- 작은 처리 단위가 바뀌는 지점에서는 빈 줄과 들여쓰기를 사용해 읽기 단위를 드러낸다. 문장이 바뀌었는데도 선언과 호출을 과하게 밀집시키는 형식은 피한다.
- 추상화는 "나중에 재사용할 수도 있음"이 아니라 "지금 여기서 의미가 늘어나는가"를 기준으로 판단한다. 재사용 근거가 없으면 선제 분리를 하지 않는다.

## Lightweight Flow

- 동작 변경이 없는 리팩터링, 빌드/타입 오류 수정, 테스트 보강, 죽은 코드 제거, 문서/주석 정리는 `docs/REQUIREMENTS.md` 갱신 없이 진행할 수 있다.
- 기존 요구사항 범위 안의 버그 수정은 `docs/REQUIREMENTS.md` 갱신 없이 진행할 수 있다. 다만 PR에는 기존 요구사항 범위 안의 수정임을 간단히 명시한다.
- 탐색, 디버깅, 프로토타이핑, 실험 단계에서는 ADR 없이 빠르게 진행할 수 있다. 다만 최종적으로 채택되는 결정이 생기면 그 시점에 ADR을 작성한다.
- 문서 템플릿, 파일명, 섹션 구조 같은 디렉토리별 상세 작성 기준은 해당 경로의 `README.md`와 템플릿 문서를 따른다.

## ADR Trigger

- ADR은 아키텍처 변경, 인증/보안 정책 변경, 상태 관리 방식 변경, 배포/운영 정책 변경처럼 되돌리기 어렵거나 팀 합의가 필요한 경우에만 작성한다.
- 구현 세부 조정, 국소 버그 수정, UI 문구 변경, 단순 리팩터링은 ADR 없이 진행한다.

## Personal Learning Docs Policy

- 개인 학습용 설명 문서의 파일 구성, 흐름, 네이밍 기준은 `docs/learn/README.md`를 따른다.
- 트러블슈팅 성격의 작업이면 `docs/learn/troubleshooting/README.md`를 먼저 읽고, 그 기준과 템플릿에 따라 같은 문제 축의 단일 문서로 누적 작성한다.
- 개인 학습용 `learn` 문서 생성이나 갱신만 있는 요청은 원칙적으로 `prompts/`에 남기지 않는다.
- 다만 같은 작업 안에서 팀 정책, 프로젝트 구조, 공식 문서 체계 변경까지 함께 일어나면 그때만 프로젝트 작업으로 보고 `prompts/`에 남긴다.
- 사용자가 "가르쳐줘", "이해하고 싶어", "공부하고 싶어", "왜 이런지 설명해줘"처럼 개인 이해를 목표로 요청하면 우선 `learn`으로 분류한다.
- 선택지 비교, 추천안, 채택 후보 정리, 팀 기준 제안처럼 실제 의사결정에 영향을 주는 내용은 `docs/discussions/`로 분류한다.
- `learn`에서 정리한 내용이 실제 정책 제안이나 기술 선택 검토로 확장되면 `docs/discussions/` 문서로 승격한다.
- `docs/discussions/`에서 합의되거나 채택된 내용만 `docs/decisions/` ADR로 승격한다.

## Repository Context

이 섹션은 AI가 저장소의 역할과 경계를 빠르게 이해하도록 돕는다.

예시:

```md
## Repository Context

이 저장소는 서비스 아키텍처의 한 구성 요소다.

Stack
- Spring Boot
- Spring Security
- OAuth2

Key Endpoints
- /auth/sso/start
- /auth/exchange
- /auth/me
```

## PR Expectations

- PR 본문에 사용한 프롬프트 로그 경로를 포함한다.
- 결정 사항이 있으면 ADR 경로를 함께 링크한다.
- REQUIREMENTS 변경 여부(포함/미포함)와 사유를 명시한다.
