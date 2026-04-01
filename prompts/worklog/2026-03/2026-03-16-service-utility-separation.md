# 2026-03-16 Service Utility Separation

## Step 1. 작업 요약

- 목적: 서비스 계층이 비즈니스 규칙과 오케스트레이션만 담당하도록 유틸성 정규화/파싱 책임을 분리한다.
- 요구사항 변경: `docs/REQUIREMENTS.md`의 계층 책임 원칙에 서비스와 유틸 분리 원칙을 추가하되, 현재 구조에서는 과한 포트/어댑터 추상화 대신 단순한 유틸 클래스 분리를 우선하도록 명시했다.
- ADR 추가: `docs/decisions/007-separate-utility-concerns-from-service-layer.md`에 현재 코드베이스에 맞는 단순한 유틸 분리 결정을 기록했다.
- 구현 변경: `TextNormalizer`, `OrderedSortKeyGenerator`, `DocumentJsonCodec`를 별도 클래스로 두고 `WorkspaceServiceImpl`, `DocumentServiceImpl`, `BlockServiceImpl`, `DocumentApiMapper`가 이를 사용하도록 정리했다.
