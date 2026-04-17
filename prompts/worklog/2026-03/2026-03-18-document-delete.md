# 2026-03-18 Document Delete

## Step 1. API 스켈레톤 추가

- 목적: 문서 삭제 API 엔드포인트와 서비스 시그니처를 먼저 연결한다.
- 변경 내용: `documents-api` 컨트롤러, `documents-core` 서비스 인터페이스, `documents-infrastructure` 최소 구현에 문서 삭제 진입점을 추가했다.
- 제외 범위: 초기 단계에서는 soft delete 비즈니스 로직과 block 연계 삭제는 제외했다.
- 검증: Gradle `compileJava`로 모듈 컴파일 확인 계획을 잡았다.

## Step 2. 서비스 soft delete 로직 구현

- 목적: 문서 1건 soft delete와 소속 활성 블록 soft delete 흐름을 먼저 구현한다.
- 변경 내용: 활성 문서만 삭제 가능하도록 하고, soft delete 문서는 `DOCUMENT_NOT_FOUND`로 처리했다.
- 변경 내용: 삭제 시 동일한 `deletedAt`과 `actorId`로 문서와 활성 블록 삭제를 함께 반영하도록 서비스 로직을 연결했다.

## Step 3. 블록 삭제 책임 위임

- 목적: `DocumentServiceImpl`이 블록 bulk soft delete를 직접 다루지 않도록 의존 방향을 정리한다.
- 변경 내용: `BlockService` 계약을 추가하고, `BlockServiceImpl`이 `BlockRepository` 수정 쿼리 기반 bulk soft delete를 수행하도록 위임했다.
- 판단: 문서 서비스는 문서 오케스트레이션만 담당하고, 블록 삭제 세부는 블록 서비스로 모으는 쪽이 책임 경계에 맞았다.

## Step 4. 벌크 update 정책 정리

- 목적: 문서 삭제와 문서 소속 블록 삭제를 repository bulk update 기반으로 정리한다.
- 변경 내용: `DocumentRepository`, `BlockRepository`, `DocumentServiceImpl`을 벌크 update 기준으로 정리했다.
- 적용 정책: 문서 벌크 update 결과가 0건이면 `DOCUMENT_NOT_FOUND`로 처리했다.

## Step 5. WebMvc와 서비스 테스트 보강

- 목적: 삭제 API의 웹 계층 계약과 서비스 규칙을 테스트로 고정한다.
- 변경 내용: `DocumentControllerWebMvcTest`에 정상 삭제, 문서 없음, 이미 삭제됨, 인증 헤더 누락 케이스를 추가했다.
- 변경 내용: `DocumentServiceImplTest`에 활성 문서 삭제, block soft delete 위임, 동일 actor/deletedAt 전달, 문서 없음, 이미 삭제됨 케이스를 추가했다.

## Step 6. API 통합 테스트 보강

- 목적: 실제 삭제 요청이 문서와 블록 상태에 기대대로 반영되는지 조립 테스트로 검증한다.
- 변경 내용: `DocumentApiIntegrationTest`에 삭제 성공, 조회 실패, 문서 없음 케이스를 추가했다.
- 검증 포인트: 문서 `deletedAt` 저장, 소속 활성 블록 soft delete, 다른 문서 블록 보존.

## Step 7. 하위 문서 bulk soft delete 누락 보완

- 목적: 상위 문서만 soft delete 되던 누락을 보완해 활성 하위 문서와 각 문서의 블록까지 함께 삭제되게 맞춘다.
- 변경 내용: 하위 문서를 재귀 수집한 뒤 동일한 `deletedAt`과 `actorId`로 문서 bulk update 1회를 수행하고, 이후 각 문서의 블록 soft delete를 위임하도록 수정했다.
- 검증 포인트: 하위 문서 soft delete 전파, 다른 문서 보존, `documents` soft delete update SQL 1회 실행.
