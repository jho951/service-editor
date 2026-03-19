# 2026-03-19 Block Structured Content Migration

- 작업 목적: `Block` 본문을 plain text에서 structured content JSON 기준으로 전환하기 위한 설계와 초기 코드 변경을 단계적으로 정리한다.
- 범위: 요구사항/검토 문서/ADR 반영, `Block` 엔티티 1단계 전환, 이후 API/서비스/테스트 마이그레이션 기준선 수립
- 관련 문서: `docs/REQUIREMENTS.md`, `docs/discussions/2026-03-19-block-props-review.md`, `docs/discussions/2026-03-19-block-structured-content-strategy.md`, `docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md`

## Step 1. `props`/structured content 방향 검토

- 기존 `Block.text`와 `props.text`를 함께 두는 구조는 canonical source가 2개가 되어 부적절하다고 판단했다.
- 블록 단위 스타일 객체보다 segment + marks 기반 structured content가 에디터 요구와 더 맞는다고 정리했다.
- `BlockType`과 content 내부 `type/format`은 같은 층위가 아니며 분리해야 한다고 정리했다.

## Step 2. 동시성 로드맵 검토

- MVP/V1에서는 block 단위 optimistic lock을 유지하고, 이후 `transactions`/operation 기반 저장으로 확장하는 로드맵을 채택 방향으로 정리했다.
- OT/CRDT는 초기 도입 대상이 아니라, V1 이후 실제 충돌 패턴과 운영 요구를 확인한 뒤 검토하는 방향으로 정리했다.
- 핵심 판단은 "본문 모델은 먼저 바꾸고, 충돌 세분화는 뒤로 미룬다"였다.

## Step 3. 요구사항 및 ADR 반영

- `docs/REQUIREMENTS.md`를 `text` 중심 plain text 규칙에서 `content` structured JSON 규칙으로 갱신했다.
- v1 허용 mark를 `bold`, `italic`, `textColor`, `underline`, `strikethrough`로 명시했다.
- `textColor`는 프론트가 바로 사용할 수 있는 `#RRGGBB` 형식으로 고정했다.
- block 단위 optimistic lock과 이후 staged concurrency roadmap을 요구사항에 반영했다.
- `docs/decisions/012-adopt-structured-text-content-and-staged-concurrency-roadmap.md`를 추가했다.

## Step 4. `Block` 엔티티 1단계 전환

- `Block` 저장 필드를 `text`에서 `content_json` 컬럼 기반으로 전환했다.
- 후속 단계 전까지 기존 코드가 즉시 깨지지 않도록 `getText`/`setText`와 builder `text(...)` 호환 메서드를 임시 유지했다.
- 새 기준 접근자는 `getContent()`/`setContent()`다.
- 검증: `./gradlew :documents-core:compileJava` 성공

## 다음 단계

- `Block` API DTO와 응답 매퍼를 `text`에서 `content` 기준으로 전환
- 서비스 로직을 `content` 기준으로 전환
- validation/codec 도입
- 테스트와 통합 API 시나리오를 structured content 기준으로 수정

## Step 5. 블록 생성 경로 `content` JSON 전환

- 블록 생성 요청 DTO를 `text`에서 `JsonNode content` 기준으로 변경했다.
- `BlockJsonCodec`를 추가해 create 요청은 JSON object를 받고, 서비스 계층에는 직렬화된 문자열을 넘기도록 연결했다.
- 블록 응답 DTO와 매퍼에 `content`를 추가해 생성 응답에서 structured content를 그대로 반환하도록 했다.
- `BlockService.create(...)`와 구현체를 `content` 기준으로 전환했다.
- 생성 관련 테스트를 `content` 기준으로 수정하고, 저장소에는 직렬화된 JSON 문자열이 저장되는 점까지 검증했다.
- 검증: `./gradlew :documents-api:test --tests com.documents.api.block.BlockControllerWebMvcTest :documents-infrastructure:test --tests com.documents.service.BlockServiceImplTest :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest`

## Step 6. 블록 생성 요청 `content` validation 추가

- `ValidBlockContent`, `BlockContentValidator`를 추가해 블록 생성 요청에서 structured content JSON 스키마를 서버 입구에서 검증하도록 했다.
- 현재 검증 범위는 `format=rich_text`, `schemaVersion=1`, 비어 있지 않은 `segments`, segment의 `text`/`marks`, 허용 mark 타입, `textColor`의 `#RRGGBB` 형식, 전체 plain text 길이 합 `10,000` 이하다.
- WebMvc 테스트에 `format`, mark 타입, `textColor` 형식 실패 케이스를 추가했다.
- Boot 통합 테스트에 허용되지 않은 mark 타입 요청 실패 케이스를 추가했다.
- 검증: `./gradlew :documents-api:test --tests com.documents.api.block.BlockControllerWebMvcTest :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest`

## Step 7. 블록 수정 경로 `content` JSON 전환

- `UpdateBlockRequest`를 `text`에서 `JsonNode content` 기준으로 변경하고, 생성과 동일한 `ValidBlockContent` 검증을 재사용하도록 했다.
- `BlockController.updateBlock(...)`은 이제 요청 `content`를 직렬화해서 서비스에 전달한다.
- `BlockService.update(...)`와 구현체를 `content` 기준으로 변경하고, 저장 필드도 `block.content`를 직접 갱신하도록 바꿨다.
- 수정 관련 WebMvc/서비스/통합 테스트를 `content` 응답 및 저장 검증 기준으로 갱신했다.
- 검증: `./gradlew :documents-api:test --tests com.documents.api.block.BlockControllerWebMvcTest :documents-infrastructure:test --tests com.documents.service.BlockServiceImplTest :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest`

## Step 8. 블록 조회 리팩토링 마무리

- 블록 목록 조회 테스트 픽스처를 plain text 대신 structured `content` JSON 저장 기준으로 바꿨다.
- WebMvc 테스트와 Boot 통합 테스트에서 조회 응답의 `content.format`, `content.segments[].text`를 직접 검증하도록 수정했다.
- 이로써 생성, 수정, 조회 핵심 경로가 모두 `content` 기반 계약으로 연결되었다.
- 검증: `./gradlew :documents-api:test --tests com.documents.api.block.BlockControllerWebMvcTest :documents-infrastructure:test --tests com.documents.service.BlockServiceImplTest :documents-boot:test --tests com.documents.api.block.BlockApiIntegrationTest`
