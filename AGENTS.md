# AGENTS 가이드

- `docs/REQUIREMENTS.md`, `docs/decisions`, `prompts`를 항상 함께 갱신합니다.
- 중요한 기술 선택은 ADR로 기록합니다.
- 재현 가능한 디버깅 절차를 `docs/runbook/DEBUG.md`에 유지합니다.


# AGENTS Working Agreement

## Purpose

이 문서는 이 저장소에서 사람과 AI 에이전트가 협업할 때의 규칙과 작업 절차를 정의합니다.

### Goal

- AI가 프로젝트의 목표, 아키텍처, 코딩 스타일을 일관되게 이해하도록 합니다.
- 모든 변경 사항이 추적 가능하고 재현 가능하도록 기록합니다.
- AI 작업이 코드베이스를 예측 가능한 방식으로 수정하도록 제한합니다.

## Ground Rules

- 기존 제품 코드(`app/`, `components/`, `libs/` 등)의 동작을 바꾸는 변경은 목적, 근거, 영향 범위를 PR에 꼭 명시합니다.
- 요구사항/가정이 바뀌면 어떤 이유로 바뀌었는지 설명하고 `docs/REQUIREMENTS.md` 갱신 여부를 묻고, 수락 시 `docs/REQUIREMENTS.md`를 함께 갱신합니다.
- 중요한 기술적 결정(트레이드오프, 정책 변경, 되돌리기 어려운 선택)은 ADR로 남깁니다.
- 모든 AI 작업은 `prompts/`에 최소 1개 이상 로그를 남깁니다.
- 코드 주석은 꼭 필요한 경우에만 추가하고, 기본 문체는 명사형/단답형으로 유지합니다.
- 에러 메시지와 예외 메시지는 특별한 사유가 없으면 기본적으로 한글로 작성합니다.

## Lightweight Flow

- 동작 변경이 없는 리팩터링, 빌드/타입 오류 수정, 테스트 보강, 죽은 코드 제거, 문서/주석 정리는 `docs/REQUIREMENTS.md` 갱신 없이 진행할 수 있습니다.
- 기존 요구사항 범위 안의 버그 수정은 `docs/REQUIREMENTS.md` 갱신 없이 진행할 수 있습니다. 다만 PR에는 기존 요구사항 범위 안의 수정임을 간단히 명시합니다.
- 탐색, 디버깅, 프로토타이핑, 실험 단계에서는 ADR 없이 빠르게 진행할 수 있습니다. 다만 최종적으로 채택되는 결정이 생기면 그 시점에 ADR을 작성합니다.
- 프롬프트 로그는 작은 작업일 경우 날짜, 작업 목적, 핵심 변경만 적은 3~5줄 요약 형식으로 남겨도 충분합니다.

## ADR Trigger

- ADR은 아키텍처 변경, 인증/보안 정책 변경, 상태 관리 방식 변경, 배포/운영 정책 변경처럼 되돌리기 어렵거나 팀 합의가 필요한 경우에만 작성합니다.
- 구현 세부 조정, 국소 버그 수정, UI 문구 변경, 단순 리팩터링은 ADR 없이 진행합니다.

## Required Artifacts

- Requirements: `docs/REQUIREMENTS.md`
- Decisions (ADR): `docs/decisions/`
- Prompt logs: `prompts/`
- Debug runbook: `docs/runbook/DEBUG.md`
- Technical explainers: `prompts/explainers/`

## Technical Explainers Policy

- 알고리즘, 정렬 정책, 캐시 전략, 동시성 제어, 계층 모델처럼 코드만 보고 빠르게 파악하기 어려운 핵심 기술 구조는 `prompts/explainers/`에 설명 문서로 유지한다.
- 설명 문서는 날짜형 이름 대신 주제 중심의 고정 파일명으로 관리한다.
- 사용자가 알고리즘 설명, 설계 배경, 내부 동작 흐름, 메서드별 역할 정리를 요청하면 관련 explainer가 있는지 먼저 확인하고, 필요할 때만 해당 경로를 읽는다.
- 관련 코드 변경으로 explainer 내용이 달라졌다면 같은 작업에서 explainer도 함께 갱신한다.
- 모든 작업에서 `prompts/explainers/`를 자동으로 읽을 필요는 없고, 설명이 필요하거나 구조 변경 영향이 있는 경우에만 읽는다.
- 작업 로그(`prompts/*.md`)에서 중요한 기술 정책을 다뤘다면 관련 explainer 경로를 함께 남기는 것을 권장한다.

## Repository Context

이 섹션은 AI가 저장소의 역할과 경계를 빠르게 이해하도록 돕습니다.

예시:

```md
## Repository Context

이 저장소는 서비스 아키텍처의 한 구성 요소입니다.

Role
- 인증 서버
- OAuth 로그인 처리
- SSO 세션 발급

Stack
- Spring Boot
- Spring Security
- OAuth2

Key Endpoints
- /auth/sso/start
- /auth/exchange
- /auth/me
```

이 정보를 포함하면 AI가 다음을 더 정확하게 이해할 수 있습니다.

- 이 repo의 역할
- 기술 스택
- API 구조

## PR Expectations

- PR 본문에 사용한 프롬프트 로그 경로를 포함한다.
- 결정 사항이 있으면 ADR 경로를 함께 링크한다.
- REQUIREMENTS 변경 여부(포함/미포함)와 사유를 명시한다. 이런 구조는 공통 agents.md로 어때?
