# ADR 009: Document 계층은 JPA 연관관계와 DB cascade FK로 매핑한다

## 상태

채택됨

## 배경

기존 `Document` 엔티티는 `workspaceId`, `parentId`를 단순 UUID 컬럼으로만 보관했다.
이 구조는 API 계약을 구현하는 데는 충분했지만, JPA 차원에서 워크스페이스-문서, 문서-하위문서 계층을 모델링하지 못해 다음 문제가 있었다.

- 부모/자식 무결성 검증이 서비스 계층 UUID 비교에 과도하게 의존한다.
- 문서 계층 삭제 정리 시 FK 레벨 cascade 규칙을 명시적으로 표현하기 어렵다.
- 테스트/운영 정리 시 hard delete가 발생하면 self hierarchy dangling reference 가능성을 스키마가 직접 막지 못한다.

## 결정

- `Document.workspace`는 `@ManyToOne(fetch = LAZY, optional = false)`로 `Workspace`를 참조한다.
- `Document.parent`는 `@ManyToOne(fetch = LAZY)`로 상위 `Document`를 참조한다.
- `Document.children`는 `@OneToMany(mappedBy = "parent", cascade = REMOVE)`로 역방향 계층을 표현한다.
- `Document.parent` FK에는 Hibernate `@OnDelete(CASCADE)`를 적용해 물리 스키마의 `parent_id`에 `ON DELETE CASCADE`를 생성한다.
- 외부 API와 서비스 입력 계약은 기존처럼 `workspaceId`, `parentId` UUID 중심으로 유지하되, 엔티티는 연관 엔티티를 기준으로 동작하고 `getWorkspaceId()`, `getParentId()`로 호환 값을 노출한다.
- `@ManyToOne` 자체에는 `CascadeType.REMOVE`를 두지 않는다. 그 방식은 자식 삭제 시 부모 삭제로 전파되어 원하는 방향과 반대이기 때문이다.

## 영향

- 장점:
  - 문서 계층이 엔티티 모델과 스키마에 직접 반영되어 무결성이 더 분명해진다.
  - 부모 문서 hard delete 시 하위 문서가 DB 레벨에서 함께 정리된다.
  - API 응답/요청 계약은 유지하면서 내부 구현만 연관관계 기반으로 개선할 수 있다.
- 단점:
  - `documents-core`가 Hibernate annotation을 직접 사용하므로 `hibernate-core` 의존성이 필요하다.
  - 연관관계 조회와 flush 타이밍을 잘못 다루면 불필요한 update/version 증가가 생길 수 있어 서비스 로직에서 조회 중복을 줄여야 한다.
