# 2026-04-07 Docs Governance Dedup And Readability

## Step 1. 상위 README 참조 규칙과 중복 제거 기준 정리

- 목적: 문서 작성 시 `docs/README.md`와 대상 경로의 상위 `README.md`를 순서대로 확인하도록 기준을 더 명확하게 만든다.
- 변경 내용: `AGENTS.md`에 `docs/README.md` 선독과 경로별 상위 `README.md` 순차 확인 규칙을 명시했다.
- 변경 내용: 상위 소유 위치에 있는 규칙을 하위 `README.md`나 템플릿에 다시 복제하지 않는다는 원칙을 강화했다.
- 변경 내용: `docs/discussions/000-strategy-review-template.md`와 각 디렉토리 `README.md`에서 중복된 규칙 문장을 걷어내고 전역 규칙은 `docs/README.md`로 모았다.

## Step 2. Markdown 가독성 규칙 추가와 문서 템플릿 정리

- 목적: 문서 규칙과 템플릿이 같은 형식 감각으로 읽히도록 Markdown 가독성 기준을 명시한다.
- 변경 내용: `docs/README.md`에 헤더 깊이, 리스트 형식, 문단 길이, 강조 사용, 구분선 사용에 대한 가독성 규칙을 추가했다.
- 변경 내용: `docs/discussions/000-strategy-review-template.md`를 현재 가독성 규칙에 맞게 정리하고, 이미 상위 문서가 담당하는 규칙은 삭제했다.
- 변경 내용: `prompts/README.md`와 `prompts/topics/docs-and-prompt-governance.md`에도 이번 변경의 문맥이 이어지도록 최소한의 링크와 표현을 보강했다.

## Step 3. 관련 문서 대조

- 목적: 전역 규칙, 디렉토리 규칙, 템플릿이 서로 다른 말을 하지 않도록 확인한다.
- 검증 내용: `AGENTS.md`, `docs/README.md`, `docs/discussions/README.md`, `docs/learn/README.md`, `docs/explainers/README.md`, `docs/guides/README.md`, `prompts/README.md`, `docs/discussions/000-strategy-review-template.md`를 대조했다.
- 판단: 이번 변경은 제품 기능 변경이 아니라 문서 운영 규칙 정리와 템플릿 가독성 개선에 해당하므로 `docs/REQUIREMENTS.md` 갱신 대상은 아니다.
