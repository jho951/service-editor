# 2026-03-16 Test Pyramid Policy

## Step 1. 작업 요약

- 목적: API 개발 시 빠른 테스트와 전체 조립 테스트를 분리한 공통 전략을 적용한다.
- 요구사항 변경: `docs/REQUIREMENTS.md`에 테스트 피라미드, 기능별 필수 테스트, 80% 커버리지 목표를 추가했다.
- 요구사항 보강: 모든 테스트에 한글 `@DisplayName`으로 역할을 명시하는 규칙을 추가했다.
- 결정 보강: `docs/decisions/004-place-api-integration-tests-in-boot-and-keep-controllers-thin.md`에 api slice 테스트 우선 원칙을 추가했다.
- 적용 방안: Workspace 기준으로 `documents-api` slice 테스트, `documents-infrastructure` 서비스 단위 테스트, `documents-boot` 조립 테스트를 분리한다.
- 의존성 원칙: 공통 테스트 좌표는 루트 `build.gradle`에서 관리하고, 각 모듈은 필요한 최소 `testImplementation`만 선택한다.
