# 2026-03-31 AGENTS And Directory Guide Cleanup

## Step 1. AGENTS 상단 가이드와 docs 분류 기준 재구성

- 목적: `AGENTS.md` 상단에서 `docs/`, `prompts/`의 분류 기준과 핵심 운영 규칙이 한 번에 읽히도록 정리한다.
- 변경 내용: `docs/REQUIREMENTS.md`, `docs/discussions/`, `docs/decisions/`, `docs/runbook/`, `docs/roadmap/`, `docs/explainers/`, `docs/learn/`, `docs/guides/`, `prompts/`의 역할을 상단 요약부에 다시 배치했다.
- 변경 내용: `learn -> discussions -> decisions` 승격 기준과 `prompts/` 기록 기준도 상단 가이드에서 바로 읽히게 반영했다.

## Step 2. AGENTS를 라우팅 중심 구조로 정리

- 목적: 상위 `AGENTS.md`는 전역 규칙과 문서 위치만 안내하고, 실제 작성 기준은 각 디렉토리 `README.md`가 맡도록 책임을 분리한다.
- 변경 내용: `docs/README.md`를 먼저 읽고, 각 하위 디렉토리에 `README.md`가 있으면 그 기준을 따른다고 명시했다.
- 변경 내용: `docs/discussions/`, `docs/decisions/`, `docs/runbook/`, `docs/roadmap/`, `docs/explainers/`, `docs/learn/`, `docs/guides/`, `prompts/`가 각각 자신의 `README.md` 기준을 따르도록 라우팅 문구를 정리했다.
- 변경 내용: 실제로 비어 있던 `docs/README.md`, `docs/explainers/README.md`, `docs/guides/README.md`, `docs/runbook/README.md`를 보강해 라우팅 구조가 성립하도록 맞췄다.

## Step 3. AGENTS와 README 간 중복 규칙 제거

- 목적: 상위 문서와 디렉토리 README 사이에서 같은 규칙이 반복되며 충돌하지 않도록 중복을 정리한다.
- 변경 내용: `AGENTS.md`에서는 프롬프트 형식, 학습 문서 세부 규칙 같은 디렉토리 전용 상세 설명을 걷어내고 전역 분류와 트리거만 남겼다.
- 변경 내용: 세부 파일 구성, 섹션 구조, 템플릿, 문서 작성 흐름은 각 디렉토리 `README.md`가 담당하도록 정리했다.
- 검증 내용: `AGENTS.md`, `docs/README.md`, `docs/discussions/README.md`, `docs/decisions/README.md`, `docs/roadmap/README.md`, `docs/explainers/README.md`, `docs/learn/README.md`, `docs/learn/troubleshooting/README.md`, `docs/guides/README.md`, `docs/runbook/README.md`, `prompts/README.md`를 대조했다.

## Step 4. 문체와 요약 표현 통일

- 목적: 운영 문서 전반이 같은 인상으로 읽히도록 기본 문체를 정리한다.
- 변경 내용: `AGENTS.md`, `prompts/README.md`, `docs/decisions/000-adr-template.md` 등 주요 운영 문서의 안내 문장을 `한다/이다` 계열 문체로 통일했다.
- 판단: 세부 규칙을 읽을 때 문체 혼용이 적을수록 문서 체계 전체가 덜 흔들린다.

## Step 5. prompts 작업 로그 정리 기준 적용

- 목적: 같은 목표 아래 이어진 작업이 여러 프롬프트 파일로 쪼개지지 않도록 `prompts/README.md` 기준을 기존 로그에도 적용한다.
- 변경 내용: `document update`, `document delete`, `block update/move`, `editor transaction save model`처럼 하나의 작업 단위가 여러 파일로 나뉜 로그를 각각 단일 파일로 병합했다.
- 변경 내용: 루트 `prompts/`에는 날짜별 작업 로그만 두고, 예외 문서는 별도 위치로 정리하는 방향으로 재배치했다.
- 변경 내용: 기존 단일 로그들의 형식을 `## Step n. 제목` 구조로 맞췄다.
