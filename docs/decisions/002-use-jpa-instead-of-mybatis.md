# ADR 002: 영속 계층을 MyBatis에서 JPA로 전환

## 상태

채택됨

## 배경

초기 구조에는 `documents-infrastructure` 모듈에 MyBatis mapper XML과 타입 핸들러가 포함되어 있었다.
현재 저장소는 CRUD 중심의 문서 서비스 뼈대를 빠르게 정리하는 단계이며, 도메인 모델과 영속 모델을 분리해서 운영할 정도의 복잡도까지는 아직 도달하지 않았다.

## 결정

영속 계층의 기본 표준을 MyBatis에서 Spring Data JPA로 전환한다.

- 도메인 모델 `Document`를 JPA 엔티티로 매핑한다.
- 공통 필드 `createdAt`, `updatedAt`, `version`은 `@MappedSuperclass`로 관리한다.
- `documents-infrastructure`는 mapper XML 대신 `JpaRepository` 기반 저장소를 사용한다.
- 로컬/운영 설정과 디버그 런북은 Hibernate 로그와 JPA 설정 기준으로 유지한다.

## 영향

- 장점:
  - 단순 CRUD 구현 속도가 빨라지고 XML mapper 유지 비용이 사라진다.
  - 낙관적 락과 공통 감사 필드를 엔티티 규약으로 일관되게 관리할 수 있다.
  - 테스트에서 H2 기반 컨텍스트 검증을 구성하기 쉬워진다.
- 단점:
  - 복잡한 동적 SQL이나 대량 배치 최적화가 필요해지면 별도 전략이 필요하다.
  - 엔티티 매핑과 스키마 불일치가 런타임까지 드러날 수 있으므로 검증 루틴이 중요해진다.
