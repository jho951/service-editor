# 2026-03-16 Document Create

## Step 1. 작업 요약

- 목적: `docs/REQUIREMENTS.md` 기준으로 Workspace 하위 Document 생성 기능을 구현한다.
- 요구사항 변경: 없음. 기존 요구사항의 `POST /v1/workspaces/{workspaceId}/documents` 범위를 코드에 반영했다.
- 결정 기록: 없음. 기존 Workspace 서비스와 공통 응답/예외 정책을 그대로 사용했다.
- 구현 범위: Document 엔티티를 요구사항 구조로 정리하고, 생성 API/DTO/서비스 검증, WebMvc 테스트, 통합 테스트를 추가한다.
- 후속 정리: DTO 내부 `ObjectMapper` 사용을 제거하고 API 매퍼로 JSON 변환 책임을 이동했다. 쓰기 엔드포인트의 `X-User-Id`는 인증 전제에 맞게 필수로 조정했다.
- 에러 코드 정책: 범용 `RESOURCE_NOT_FOUND` 대신 `WORKSPACE_NOT_FOUND`, `DOCUMENT_NOT_FOUND`처럼 도메인별 식별 가능한 코드를 기본값으로 채택했다.
- 테스트 보강: `documents-infrastructure`에 `DocumentServiceImplTest`를 추가해 문서 생성 서비스의 성공/실패 분기를 빠르게 검증했다.
- 테스트 정리: `DocumentControllerWebMvcTest`에서 공통 에러 JSON envelope를 `httpStatus/message/code/data`까지 검증하도록 강화하고, `DocumentServiceImplTest`는 재사용 헬퍼로 중복을 줄였다.
- 입력 검증 보강: `icon`/`cover`는 `type`과 `value`를 가진 객체만 허용하도록 선언적 검증을 추가했다. 관련 slice/boot 테스트에도 잘못된 JSON 스키마 케이스를 포함했다.
- 요구사항 반영: `docs/REQUIREMENTS.md`에 v1의 `icon`/`cover` 최소 허용 JSON 스키마를 명시했다.
