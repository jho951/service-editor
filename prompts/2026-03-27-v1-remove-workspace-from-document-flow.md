# 2026-03-27 v1 Remove Workspace From Document Flow

- 작업 목적: v1 범위에서 Workspace 선행 의존을 제거하고, 문서 생성/목록/휴지통 흐름을 `@CurrentUserId` 기반 사용자 소유 문서 모델로 단순화한다.
- 관련 요구사항: `docs/REQUIREMENTS.md`
- 관련 결정: `docs/decisions/018-remove-workspace-from-v1-document-flow.md`
- 관련 검토: `docs/discussions/2026-03-27-v1-remove-workspace-from-document-flow-review.md`

## Step 1. 문서 소유 모델을 workspace 기준에서 user 기준으로 전환

- `Document` 영속 모델에서 Workspace 연관 흔적을 제거하고, 문서 생성/조회/이동 검증 기준을 `workspaceId` 대신 `createdBy(userId)`로 바꿨다.
- `DocumentRepository`, `DocumentService`, `DocumentServiceImpl`은 활성 문서 목록, 휴지통 목록, 형제 조회를 모두 사용자 소유 기준으로 조회하도록 정리했다.

## Step 2. 문서 API 입력 계약을 사용자 기준으로 정리

- 문서 생성, 문서 목록 조회, 휴지통 조회는 `workspaceId` 입력 없이 `@CurrentUserId`만으로 동작하도록 변경했다.
- `DocumentController`는 클래스 레벨 `@RequestMapping("/documents")`로 묶고, 메서드 매핑에서 중복 `/documents` prefix를 제거했다.

## Step 3. 응답 계약과 테스트 데이터를 새 모델에 맞춤

- `DocumentResponse`에서 `workspaceId`를 제거했다.
- `DocumentControllerWebMvcTest`, `DocumentServiceImplTest`, `DocumentApiIntegrationTest` 등 문서 관련 테스트는 사용자 소유 문서 계약에 맞게 갱신했다.
- 테스트 내부 helper와 변수명도 `workspaceId` 대신 owner/user 의미로 정리했다.

## Step 4. Workspace 전용 코드는 활성 source set에서 제거하고 백업으로 분리

- `WorkspaceController`, Workspace DTO, 엔티티, 서비스, 리포지토리, 전용 테스트는 더 이상 active code/source set에 남기지 않았다.
- 추후 재도입 검토를 위해 `backup/workspace/` 아래에 동일 코드를 백업 자산으로 이동했다.
- `WORKSPACE_NOT_FOUND` 등 현재 활성 흐름에서 쓰지 않는 Workspace 전용 오류 코드도 active code에서 제거했다.

## Step 5. 스키마/조립 테스트를 현재 계약에 맞게 재정렬

- `PersistenceSchemaIntegrationTest`는 더 이상 `WORKSPACES` 테이블이나 `FK_DOCUMENTS_WORKSPACE`를 기대하지 않도록 수정했다.
- `DocumentTransactionConcurrencyIntegrationTest`, `BlockApiIntegrationTest`, `DocumentTransactionApiIntegrationTest`의 문서 fixture는 Workspace 없이 생성되도록 정리했다.
- `DocumentApiIntegrationTest`는 현재 문서 계약 기준의 통합 검증만 남기도록 다시 구성했다.

## Step 6. 문서와 추후 재도입 계획 기록 정리

- `docs/REQUIREMENTS.md`에 v1에서 Workspace를 활성 범위에서 제외하고, active source set에서는 제거한 뒤 `backup/workspace/`에 보관한다는 정책을 반영했다.
- `docs/runbook/DEBUG.md`, 검토 문서, ADR, roadmap에 현재 사용자 소유 문서 기준과 Workspace 재도입 검토 포인트를 반영했다.

## 테스트 실행 결과

- `env GRADLE_USER_HOME=/tmp/gradle ./gradlew test` 통과

## 변경 경로

- 요구사항: `docs/REQUIREMENTS.md`
- 검토 문서: `docs/discussions/2026-03-27-v1-remove-workspace-from-document-flow-review.md`
- 결정 문서: `docs/decisions/018-remove-workspace-from-v1-document-flow.md`
- 후속 계획: `docs/roadmap/v2/workspace/workspace-reintroduction.md`
