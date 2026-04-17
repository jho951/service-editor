# ADR 010: Block 계층은 JPA 연관관계와 DB cascade FK로 매핑한다

## 상태

채택됨

## 배경

기존 `Block` 엔티티는 `documentId`, `parentId`를 단순 UUID 컬럼으로만 보관했다.
이 구조는 API 계약 유지에는 충분했지만, 블록 트리와 문서 소속을 JPA 차원에서 직접 모델링하지 못해 다음 문제가 있었다.

- 부모 블록/문서 무결성 검증이 서비스 계층 UUID 비교에 과도하게 의존한다.
- 문서 hard delete 또는 블록 계층 정리 시 FK 레벨 cascade 규칙을 스키마가 직접 표현하지 못한다.
- 테스트/운영 정리에서 direct delete가 발생하면 block self hierarchy와 document 소속 FK에 dangling reference 위험이 남는다.

## 결정

- `Block.document`는 `@ManyToOne(fetch = LAZY, optional = false)`로 `Document`를 참조한다.
- `Block.parent`는 `@ManyToOne(fetch = LAZY)`로 상위 `Block`을 참조한다.
- `Block.children`는 `@OneToMany(mappedBy = "parent", cascade = REMOVE)`로 역방향 계층을 표현한다.
- `Block.document`, `Block.parent` FK에는 Hibernate `@OnDelete(CASCADE)`를 적용해 물리 스키마의 `document_id`, `parent_id`에 `ON DELETE CASCADE`를 생성한다.
- 외부 API와 서비스 입력 계약은 기존처럼 `documentId`, `parentId` UUID 중심으로 유지하되, 엔티티는 연관 엔티티를 기준으로 동작하고 `getDocumentId()`, `getParentId()`로 호환 값을 노출한다.
- `@ManyToOne` 자체에는 `CascadeType.REMOVE`를 두지 않는다. 그 방식은 자식 삭제 시 부모 삭제로 전파되어 원하는 방향과 반대이기 때문이다.

## 영향

- 장점:
  - 블록 트리와 문서 소속이 엔티티 모델/스키마에 직접 드러난다.
  - 문서 또는 부모 블록 hard delete 시 하위 블록이 DB 레벨에서 함께 정리된다.
  - API 계약은 유지하면서 내부 로직을 연관관계 중심으로 단순화할 수 있다.
- 단점:
  - `documents-core`가 Hibernate annotation을 직접 사용하므로 `hibernate-core` 의존성에 계속 의존한다.
  - 서비스에서 같은 부모/문서를 중복 조회하면 불필요한 flush나 version 증가가 생길 수 있어 조회 중복을 줄여야 한다.
