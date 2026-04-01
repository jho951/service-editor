# 2026-03-17 Block Create

## Step 1. 작업 요약

- 목적: `docs/REQUIREMENTS.md` 기준으로 `POST /v1/documents/{documentId}/blocks` TEXT 블록 생성 기능과 정렬 키 정책을 구현한다.
- 요구사항 변경: 있음. ordered sibling의 `sortKey` 정책을 gap 기반 lexicographic key로 구체화했고, gap 고갈 시 `SORT_KEY_REBALANCE_REQUIRED`를 반환하도록 요구사항을 보강했다.
- 결정 기록: `docs/decisions/008-adopt-gap-based-lexicographic-sort-key-policy.md`에 문서와 블록이 공유할 정렬 키 정책을 기록했다.
- 구현 범위: `Block` 엔티티/서비스/저장소, 블록 생성 API/DTO/응답 매퍼, gap 기반 `sortKey` 생성 유틸, 서비스/API/boot 테스트, 스키마 검증을 추가했다.
- 공용화: 블록 전용 generator를 `OrderedSortKeyGenerator`로 승격해 이후 Document 등 다른 ordered sibling 도메인에서도 같은 정책을 재사용할 수 있게 정리했다.
- 검증: `./gradlew :documents-infrastructure:test`, `./gradlew :documents-api:test`, `./gradlew :documents-boot:test`로 블록 생성 흐름과 JPA 스키마를 확인했다.
