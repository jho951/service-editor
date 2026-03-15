# 계획 템플릿

## 목표

- `document`와 `block` 도메인 기준으로 CRUD 기능을 설계하고 구현 가능한 상태까지 작업 순서를 정의한다.
- 현재 남아 있는 `Drawer` 명명과 `Document` 경로의 불일치를 먼저 정리한 뒤, API, 서비스, 저장소, DB 매핑을 일관된 구조로 맞춘다.
- 멀티모듈 구조(`documents-api`, `documents-core`, `documents-infrastructure`, `documents-boot`) 안에서 문서와 블록을 분리된 책임으로 관리할 수 있게 한다.

## 제약

- 이번 단계에서는 실제 CRUD 코드를 작성하지 않는다.
- 기존 멀티모듈 경계는 유지한다.
- 요구사항 변경이 생기면 `docs/REQUIREMENTS.md` 갱신 여부를 먼저 판단해야 한다.
- `document`와 `block` 관계가 1:N인지, 블록 정렬/중첩/타입 정책이 있는지는 구현 전 확정이 필요하다.
- 외부 인증/권한 정책(JWT 등)은 현재 구조에 없으므로 CRUD 범위에 자동 포함하지 않는다.

## 단계

1. 도메인 명명 정리
   `Drawer`, `DrawerService`, `DrawerServiceImpl` 등 기존 명명을 `Document` 기준으로 정리하고, 파일 경로와 클래스명, 패키지 참조를 일치시킨다.
2. 요구사항과 데이터 모델 확정
   `document`와 `block`의 필드, 식별자, 생성/수정 시점, 정렬 순서, 삭제 이방식(soft/hard), 문서-블록 관계를 정의한다.
3. 코어 계층 모델 설계
   `documents-core`에 `Document`, `Block`, 관련 서비스 인터페이스와 도메인 규칙을 배치한다.
4. 인프라 계층 설계
   `documents-infrastructure`에 JPA 엔티티/리포지토리 전략, 영속 모델 매핑, 트랜잭션 경계를 정의한다.
5. API 계약 설계
   `documents-api`에 `document` CRUD와 `block` CRUD 또는 문서 하위 리소스 API(`/documents/{documentId}/blocks`)를 설계하고 요청/응답 DTO를 정의한다.
6. 구현 순서 확정
   먼저 `document` CRUD를 완성하고, 이후 `block` 생성/조회/수정/삭제와 문서별 목록 조회를 붙인다.
7. 검증 계획 수립
   서비스 단위 테스트, repository 연동 검증, 컨트롤러 요청/응답 검증, 기본 디버깅 절차를 정리한다.
8. 문서 동기화
   최종 채택된 구조에 따라 `docs/REQUIREMENTS.md`, 필요 시 ADR, `docs/runbook/DEBUG.md`, `prompts/` 로그를 함께 갱신한다.

## 리스크와 대응

- 리스크: 현재 경로명은 `Document`인데 실제 클래스 내용은 `Drawer`로 남아 있어 구현을 바로 시작하면 참조 혼선이 커질 수 있다.
- 대응: CRUD 작업 전에 명명 정리 커밋을 먼저 분리한다.
- 리스크: `block`의 구조가 단순 텍스트 블록인지, 타입별 JSON payload를 갖는 블록인지 확정되지 않으면 스키마가 쉽게 흔들린다.
- 대응: 필드와 타입 정책을 먼저 문서화하고, 가변 payload가 필요하면 확장 가능한 스키마로 설계한다.
- 리스크: 문서 저장 단위를 문서 전체 JSON으로 둘지 블록 단위 레코드로 둘지에 따라 API와 JPA 매핑 전략이 크게 달라진다.
- 대응: 조회/수정 패턴을 기준으로 저장 전략을 먼저 선택하고 필요 시 ADR로 남긴다.
- 리스크: 블록 정렬, 이동, 부분 수정 요구가 뒤늦게 추가되면 CRUD만으로는 부족할 수 있다.
- 대응: 기본 CRUD 외에 순서 변경 API 필요 여부를 초기 설계에서 같이 판단한다.
