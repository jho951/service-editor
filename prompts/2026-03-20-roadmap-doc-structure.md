# 2026-03-20 roadmap 문서 구조 추가

- 작업 목적: 구현 완료된 기능 기준으로 후속 Todo와 고도화 항목을 남길 공통 문서 구조를 `docs` 아래에 추가
- 변경 범위: `AGENTS.md`, `docs/roadmap/README.md`, `docs/roadmap/v2/blocks/block-delete.md`
- 구조 선택: 역할이 직접 드러나는 `docs/roadmap/` 사용, 상위 버전 폴더 아래 도메인/기능 파일(`v2/blocks/block-delete.md`) 배치
- 작성 원칙: 신규 기능 아이디어 나열이 아니라, 현재 구현 기능 기준의 고도화, 검증 보강, 정책 보완, 운영 고려사항을 기록

## Step 2. v1 우선순위 문구 보강

- 작업 목적: v1이 성능 최적화보다 기능 작동과 정책 확정을 우선하는 단계라는 점을 문서에 명시
- 변경 범위: `docs/REQUIREMENTS.md`, `docs/roadmap/README.md`
- 반영 내용: v1은 기능 정합성과 예측 가능한 동작을 우선하고, 성능·운영 고도화는 후속 버전 로드맵에서 관리한다는 문구 추가
