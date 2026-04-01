# REQUIREMENT.md

**서비스명:** document-service
**버전:** v1.0-draft
**문서 목적:** 개발 착수용 요구사항 기준서
**작성 기준:** 사용자 제공 요구사항 초안 + DB 설계 문서 + API 명세 초안

## 0. 현재 구조 메모

- 현재 코드는 Spring Boot Gradle 멀티모듈 구조를 사용한다.
- 실행 모듈은 `documents-boot`이다.
- 웹 계층은 `documents-api`, 도메인 계약은 `documents-core`, JPA 기반 영속 구현은 `documents-infrastructure`에 위치한다.
- 실행 애플리케이션 식별자는 `documents-app`, 로컬/도커 기본 데이터베이스 식별자는 `documentsdb`를 사용한다.
- 이번 구조 변경은 기능 요구사항 변경이 아니라 구현 구조 정리 목적이며, 기존 API 동작은 유지 대상으로 본다.
- 영속 기술은 MyBatis 대신 Spring Data JPA를 기본 표준으로 사용한다.
- Java 코드는 IntelliJ의 `Naver-coding-convention-v1.2` 프로젝트 포매터를 기준으로 작성하고 정렬한다.
- 저장소 기준 포매터 설정은 `.idea/codeStyles/Project.xml`과 `.editorconfig`의 Java 섹션을 따른다.

---

## 1. 문서 개요

`document-service`는 Notion 유사 문서 시스템에서 **문서 메타데이터와 문서 콘텐츠 블록 구조를 저장, 조회, 수정, 삭제**하는 서비스를 의미한다.

이 서비스는 다음 두 가지를 함께 소유한다.

1. **문서(Document) 계층 구조**
2. **문서 내부 블록(Block) 트리 구조**

현재 단계의 목표는 **Notion형 문서 서비스의 최소 기능(MVP)** 을 안정적으로 제공하는 것이다.
다만 블록 타입 종류는 축소하여, **v1에서는 `TEXT` 타입 블록 1종만 지원**한다.
또한 v1은 대규모 트래픽·대용량 트리 최적화보다 **핵심 기능의 정확한 작동과 정책 확정**을 우선하며, 성능·운영 고도화는 이후 버전에서 점진적으로 보완한다.

핵심 요구사항은 다음 한 문장으로 요약된다.

> **문서는 계층 구조를 가지며, 각 문서의 콘텐츠는 ordered block tree로 저장된다. 현재 block.type은 `TEXT`만 지원하고, TEXT 블록의 본문은 structured content JSON으로 저장된다.**

---

## 2. 서비스 목표

### 2.1 비즈니스 목표
- 사용자가 로그인 직후 자신의 문서를 바로 생성하고 관리할 수 있어야 한다.
- 각 문서는 부모-자식 관계를 갖는 계층 구조를 형성할 수 있어야 한다.
- 각 문서의 본문은 블록 트리 구조로 저장되어야 한다.
- 블록은 생성, 수정, 삭제, 이동, 재정렬이 가능해야 한다.
- 자동 저장(autosave) 시나리오를 감당할 수 있어야 한다.
- v1에서는 블록 타입은 최소화하되, TEXT 블록 내부는 제한된 rich text mark를 지원한다.

### 2.2 기술적 목표
- soft delete를 기본 삭제 전략으로 사용한다.
- 문서 및 블록 수정 시 낙관적 락(optimistic locking)을 적용한다.
- 문서/블록 트리의 무결성을 보장한다.
- 향후 리치 텍스트, 협업 편집, 파일 블록 등으로 확장 가능해야 한다.
- v1의 구현은 우선 기능 정합성과 예측 가능한 동작을 기준으로 설계하고, 성능 최적화와 운영 고도화는 측정 결과와 제품 우선순위에 따라 후속 버전에서 강화한다.

---

## 3. 서비스 경계

## 3.1 document-service가 소유하는 것
- 사용자 소유 문서 메타데이터
- 문서의 부모-자식 계층 구조
- 문서 제목, 아이콘, 커버 등 기본 메타데이터
- 문서 내부 블록 구조
- 블록 생성 / 수정 / 삭제 / 이동 / 정렬
- 블록 계층 구조 유지
- 문서/블록 버전 관리
- autosave 대상 콘텐츠 데이터
- soft delete / restore 정책
- 감사 추적에 필요한 작성자 / 수정자 정보 저장

## 3.2 document-service가 소유하지 않는 것
- 로그인 / SSO
- 사용자 계정 원장(master user profile)
- 권한 정책의 최종 소유권
- 관리자 정책
- 파일 업로드 및 바이너리 저장
- 댓글 / 알림
- 검색 인덱싱 엔진
- 실시간 협업(WebSocket, presence, cursor sync, CRDT/OT)

## 3.3 다른 서비스와의 관계
- **auth-service**: 사용자 인증 및 identity 제공
- **permission-service 또는 gateway**: 읽기/쓰기 권한 판단
- **file-service**: 향후 첨부파일/이미지 블록 저장
- **editor-collab-service**: 향후 실시간 협업 기능 분리 시 사용

### 인증 진입 전제
- 이 서비스는 기본적으로 로그인된 사용자만 진입할 수 있는 내부 보호 구간으로 가정한다.
- 로그인하지 않은 사용자는 gateway 또는 상위 인증 계층에서 먼저 차단되며, 본 서비스까지 도달하지 않는다.
- 따라서 본 서비스는 인증이 완료된 사용자 컨텍스트를 전제로 비즈니스 처리를 수행한다.
- Gateway는 외부 클라이언트가 보낸 `X-User-Id`를 제거하고, 인증 성공 시에만 신뢰 가능한 `X-User-Id`를 재주입해야 한다.
- Gateway는 외부 공개 경로를 `/v1/**`로 제공하고, 내부 서비스 전달 전 경로의 `/v1` 프리픽스를 제거(rewrite)해야 한다.
- 본 서비스는 Gateway가 주입한 `X-User-Id`를 인증 컨텍스트로 사용한다.
- 본 서비스가 다른 내부 서비스(user-server 등)를 호출할 때는 호출 목적에 따라 사용자 위임 토큰 또는 서비스 전용 토큰을 구분해 사용해야 한다.

---

## 4. MVP 범위

## 4.0 워크스페이스(Workspace)
- v1 범위에서 제외한다.
- 기존 Workspace 엔티티와 API 코드는 추후 재설계를 위한 백업 자산으로만 유지한다.
- 활성 source set에서는 Workspace 전용 엔티티, 서비스, 리포지토리, API, 테스트를 제거하고 `backup/workspace/` 경로에 보관한다.
- v1 문서 API는 Workspace 존재 여부를 선행 검증하지 않는다.

## 4.1 문서(Document)
- 문서 생성
- 문서 단건 조회
- 문서 목록 조회
- 문서 수정
- 문서 soft delete
- 문서 restore
- 문서 부모 변경(계층 이동)

## 4.2 블록(Block)
- 특정 문서의 블록 전체 조회
- 텍스트 블록 생성
- 텍스트 블록 수정
- 텍스트 블록 삭제
- 텍스트 블록 이동
- 텍스트 블록 재정렬

## 4.3 현재 지원 블록 타입
- `TEXT`

## 4.4 TEXT 블록 규칙
- `block.type`은 현재 `TEXT`만 허용한다.
- `block.content`는 structured JSON object여야 한다.
- `block.content`는 최소한 `format`, `schemaVersion`, `segments`를 포함해야 한다.
- 새 `TEXT` 블록의 기본 `content`는 `segments` 1개와 빈 `text`, 빈 `marks`를 가진 empty structured content다.
- 각 segment는 `text`, `marks`를 포함해야 한다.
- v1 mark 타입은 `bold`, `italic`, `textColor`, `underline`, `strikethrough`만 허용한다.
- `textColor`는 프론트가 바로 사용할 수 있는 `#RRGGBB` 형식 hex 문자열만 허용한다.
- 링크, 멘션, inline code, 첨부 객체, 임의의 사용자 정의 mark는 v1에서 허용하지 않는다.

---

## 5. 현재 제외 범위 (Out of Scope)

다음 기능은 v1 범위에서 제외한다.

- 이미지 블록
- 파일 블록
- 체크박스 블록
- 코드 블록
- 데이터베이스 블록
- 멘션
- 링크 mark
- inline code mark
- 사용자 정의 mark 확장
- 실시간 다중 사용자 동시 편집
- 커서 공유
- presence
- CRDT / OT
- 검색 엔진 연동
- 첨부 자산(asset) 저장

---

## 6. 핵심 도메인 모델

## 6.1 User

사용자 엔티티는 본 서비스의 소유는 아니지만, 문서/블록 작성자 및 수정자 식별을 위해 참조한다.

```text
User {
  id: string
  email: string
  socialType: string
  activeStatus: string
  role: string
}
```

## 6.1.1 식별자 컬럼 명명 규칙

- 영속 스키마의 기본 키 컬럼명은 단순 `id`를 사용하지 않고 `${도메인명}_id` 형식을 사용한다.
- 예: `workspaces.workspace_id`, `documents.document_id`, `blocks.block_id`
- 외래 키 컬럼도 동일한 기준으로 대상 도메인명을 드러내는 컬럼명을 사용한다.

## 6.2 Workspace

```text
Workspace {
  id: string
  name: string
  createdBy: string | null
  updatedBy: string | null
  createdAt: datetime
  updatedAt: datetime
  version: number
}
```

### 설명
- `name`: 워크스페이스 표시 이름
- 영속 기본 키 컬럼명은 `workspace_id`를 사용한다.
- Workspace는 v1 활성 범위에서 제외하며, 추후 재설계를 위한 백업 모델로만 유지할 수 있다.
- 백업된 Workspace 관련 코드는 `backup/workspace/` 아래에서만 보관한다.
- 인증 연동 전 단계에서는 `createdBy`, `updatedBy`를 `null`로 둘 수 있다.

## 6.3 Document

```text
Document {
  id: string
  parentId: string | null
  title: string
  icon: json | null
  cover: json | null
  sortKey: string
  version: number
  createdBy: string | null
  updatedBy: string | null
  createdAt: datetime
  updatedAt: datetime
  deletedAt: datetime | null
}
```

### 설명
- 영속 기본 키 컬럼명은 `document_id`를 사용한다.
- v1 문서는 Workspace FK 없이 사용자 소유 문서로 관리한다.
- 문서 목록/휴지통 조회의 기본 필터 기준은 `createdBy`다.
- `parentId`: 상위 문서 ID. `null`이면 루트 문서. 영속 구현에서는 `parent_id` self FK로 상위 `Document`를 참조한다.
- `sortKey`: 같은 부모 아래 문서 순서 정렬용 필수 키
- `version`: 낙관적 락용 버전
- `deletedAt`: soft delete 시각
- 물리 스키마의 `parent_id` FK는 hard delete 시 하위 문서를 정리할 수 있도록 `ON DELETE CASCADE`를 사용한다. 이는 운영/테스트 정리용 안전장치이며, 비즈니스 삭제 정책 자체는 soft delete를 우선한다.

## 6.4 Block

```text
Block {
  id: string
  documentId: string
  parentId: string | null
  type: "TEXT"
  content: json
  sortKey: string
  version: number
  createdBy: string
  updatedBy: string
  createdAt: datetime
  updatedAt: datetime
  deletedAt: datetime | null
}
```

### 설명
- 영속 기본 키 컬럼명은 `block_id`를 사용한다.
- `documentId`: 블록이 속한 문서 ID. 영속 구현에서는 `document_id` FK로 `Document`를 참조한다.
- `parentId`: 상위 블록 ID. `null`이면 문서 루트 블록. 영속 구현에서는 `parent_id` self FK로 상위 `Block`을 참조한다.
- `type`: 블록 바깥 타입. 현재는 `TEXT`만 지원한다.
- `content`: TEXT 블록 본문. structured rich text JSON object
- `sortKey`: 같은 부모 아래 블록 순서 정렬용 키
- `version`: 낙관적 락용 버전
- 물리 스키마의 `document_id`, `parent_id` FK는 hard delete 시 dangling block이 남지 않도록 `ON DELETE CASCADE`를 사용한다. 비즈니스 삭제 정책 자체는 soft delete를 우선한다.

### 6.4.1 TEXT 블록 content 스키마

```json
{
  "format": "rich_text",
  "schemaVersion": 1,
  "segments": [
    {
      "text": "Hello ",
      "marks": []
    },
    {
      "text": "world",
      "marks": [
        {
          "type": "bold"
        },
        {
          "type": "textColor",
          "value": "#000000"
        }
      ]
    }
  ]
}
```

### 설명
- `content.format`: 본문 표현 포맷. v1은 `rich_text`만 허용한다.
- `content.schemaVersion`: content 스키마 버전. v1은 `1`로 시작한다.
- `content.segments`: 순서가 보장되는 텍스트 조각 배열
- `segment.text`: 실제 텍스트 조각
- `segment.marks`: 해당 텍스트 조각에 적용된 mark 목록
- `mark.type`: mark 종류. v1 허용값은 `bold`, `italic`, `textColor`, `underline`, `strikethrough`
- `mark.value`: 값이 필요한 mark에서만 사용한다. v1에서는 `textColor`에만 사용한다.
- `Block.type`과 `content.format`은 같은 의미가 아니다.
- `Block.type`은 블록 종류를 나타내고, `content.format`은 TEXT 블록 내부 본문 표현 포맷을 나타낸다.

---

## 7. 도메인 무결성 규칙

## 7.0 워크스페이스 규칙
1. Workspace 설계와 API는 v1 활성 범위에서 제외한다.
2. 기존 Workspace 코드는 추후 재설계를 위한 백업 자산으로만 유지할 수 있다.
3. v1 문서 기능은 Workspace 존재 여부를 선행 조건으로 두지 않는다.

## 7.1 문서 규칙
1. `parentId IS NULL`이면 루트 문서다.
2. 하위 문서는 반드시 같은 사용자 소유(`createdBy`)의 부모 문서를 가져야 한다.
3. 휴지통 문서는 기본 조회에서 제외한다.
4. 기본 문서 삭제 API는 hard delete를 의미해야 한다.
5. 기본 문서 삭제 시 대상 문서의 하위 문서와 각 문서 소속 블록도 함께 물리 삭제해야 한다.
6. 휴지통 이동은 기본 삭제와 분리된 `PATCH /documents/{documentId}/trash` 엔드포인트로 제공해야 한다.
7. 문서를 휴지통에 넣으면 `deletedAt`을 기록해야 한다.
8. 휴지통에 들어간 문서는 현재 테스트 기준 `deletedAt`으로부터 5분이 지나면 자동 영구 삭제 대상이 된다.
9. 자동 영구 삭제 대상에는 해당 문서의 하위 문서와 각 문서 소속 블록이 함께 포함되어야 한다.
10. 현재 테스트 기준 5분이 지나지 않은 휴지통 문서는 복구 가능해야 하며, 부모 문서가 삭제 상태인 경우 자식 문서 단독 복구는 허용하지 않는다.
11. 휴지통 문서는 휴지통 조회/복구 API를 제외한 일반 문서 목록, 단건 조회, 콘텐츠 조회의 기본 결과에서 제외해야 한다.
12. 물리 삭제가 필요한 운영/테스트 정리 상황에서는 부모 문서 hard delete 시 하위 문서 FK가 함께 정리되어 계층 dangling reference가 남지 않아야 한다.
13. 자기 자신을 부모 문서로 둘 수 없다.
14. 순환 참조(cycle)는 허용하지 않는다.
15. 같은 형제 집합(sibling scope) 내 활성 문서의 `sortKey`는 유일해야 한다.

## 7.2 블록 규칙
1. `parentId IS NULL`이면 문서 루트 블록이다.
2. 블록의 부모 블록은 반드시 같은 `documentId`를 가져야 한다.
3. 같은 형제 집합(sibling scope) 내 활성 블록의 `sortKey`는 유일해야 한다.
4. soft delete된 블록은 기본 렌더링에서 제외한다.
5. 블록 이동은 `parentId`, `sortKey`, `updatedBy`, `updatedAt`, `version`을 한 트랜잭션에서 갱신해야 한다.
6. 자기 자신을 부모 블록으로 둘 수 없다.
7. 순환 참조(cycle)는 허용하지 않는다.
8. 다른 문서의 블록을 부모로 둘 수 없다.
9. 물리 삭제가 필요한 운영/테스트 정리 상황에서는 상위 블록 또는 소속 문서 hard delete 시 하위 블록 FK가 함께 정리되어 dangling reference가 남지 않아야 한다.
10. TEXT 블록 본문은 `content` JSON 전체를 canonical source로 사용한다.
11. 같은 블록 안의 일부 구간만 바뀌더라도 v1 서버 충돌 판정 단위는 block 전체다.

## 7.3 사용자 참조 규칙
1. 쓰기 작업은 인증된 사용자만 수행할 수 있다.
2. 쓰기 작업에는 edit 권한이 필요하다.
3. 비활성 사용자에 대한 처리 정책은 auth-service / permission-service 정책을 따른다.

---

## 8. 기능 요구사항

## 8.0 워크스페이스 생성 및 조회
### 요구사항
- v1 활성 범위에서 제외한다.
- Workspace 생성/조회 API는 추후 설계 확정 시 다시 도입할 수 있다.

### 결과 조건
- 현재 v1 API 계약에는 포함하지 않는다.

## 8.1 문서 조회
### 요구사항
- 로그인한 사용자의 문서 목록을 조회할 수 있어야 한다.
- 로그인한 사용자의 휴지통 문서 목록을 조회할 수 있어야 한다.
- 특정 문서를 단건 조회할 수 있어야 한다.
- 특정 문서의 콘텐츠를 블록 트리와 함께 조회할 수 있어야 한다.
- 기본 조회는 soft delete되지 않은 리소스만 포함해야 한다.

### 결과 조건
- 문서 계층 구조 정보가 유지되어야 한다.
- 문서 버전 정보가 포함될 수 있어야 한다.
- 문서 콘텐츠 조회 시 블록 순서가 보장되어야 한다.

## 8.2 문서 생성
### 요구사항
- 사용자는 자신의 문서를 생성할 수 있어야 한다.
- 루트 문서 또는 특정 부모 문서 하위로 생성 가능해야 한다.
- 문서 제목, 아이콘, 커버를 지정할 수 있어야 한다.

### 결과 조건
- 생성된 문서는 지정된 부모와 사용자 소유 정합성을 가져야 한다.
- 생성 직후 버전 정보가 초기화되어야 한다.

## 8.3 문서 수정
### 요구사항
- 문서 제목, 아이콘, 커버를 수정할 수 있어야 한다.
- 문서 공개 상태를 `PUBLIC`, `PRIVATE` 두 값으로 수정할 수 있어야 한다.
- 낡은 버전으로 수정 요청 시 `409 Conflict`를 반환해야 한다.

### 결과 조건
- 문서 수정 API는 메타데이터 수정만 담당해야 한다.
- 부모 변경, 형제 순서 변경, 구조 이동은 별도 move API에서 처리해야 한다.
- 문서 수정 API request는 `parentId`를 받지 않아야 한다.
- 제목, 아이콘, 커버, 공개 상태가 실제로 변경되면 `Document.version`은 정확히 `1` 증가해야 한다.
- 요청 내용이 현재 상태와 완전히 같으면 no-op으로 처리하고 `Document.version`을 증가시키지 않아야 한다.

## 8.4 문서 이동 / 순서 변경
### 요구사항
- 문서의 부모 변경과 형제 순서 변경은 `POST /documents/{documentId}/move` 별도 API로 제공해야 한다.
- 문서 move API는 제목, 아이콘, 커버 수정 책임을 갖지 않고 부모 변경과 순서 변경만 담당해야 한다.
- 이동 대상 문서는 활성 문서여야 하며, 삭제된 문서는 `DOCUMENT_NOT_FOUND`로 처리해야 한다.
- 대상 문서가 존재하지 않으면 `DOCUMENT_NOT_FOUND`를 반환해야 한다.
- `targetParentId`가 `null`이면 루트 형제 집합으로 이동할 수 있어야 한다.
- `targetParentId`가 있으면 활성 문서인지 검증해야 한다.
- 대상 부모 문서는 같은 사용자 소유 문서여야 한다.
- 자기 자신을 부모로 지정하면 실패 처리해야 한다.
- 자신의 하위 문서를 부모로 지정하는 순환 이동은 실패 처리해야 한다.
- 이동 시 위치 해석용 요청 값은 `targetParentId`, `afterDocumentId`, `beforeDocumentId` 기준으로 설계해야 한다.
- `afterDocumentId`, `beforeDocumentId`가 지정되면 둘 다 같은 부모 집합에 속한 활성 형제 문서인지 검증해야 한다.
- `afterDocumentId`, `beforeDocumentId`를 동시에 받을 경우 부모 일치 여부와 순서 모순 여부를 검증해야 한다.
- `afterDocumentId`, `beforeDocumentId`를 동시에 받을 경우 두 문서는 인접한 형제여야 하며, 두 문서 사이 위치로만 해석해야 한다.
- 위치 계산 결과에 따라 같은 형제 집합 내에서 유일한 `sortKey`를 새로 계산해야 한다.
- 이동 전후 부모가 같더라도 순서만 바뀌는 reorder를 지원해야 한다.
- 이동 시 `parentId`, `sortKey`, `updatedBy`, `updatedAt`은 한 트랜잭션에서 함께 갱신해야 한다.
- 이동 실패 시 부분 반영 없이 전체 롤백되어야 한다.
- 정렬 키 공간 부족 시 현재 프로젝트 정책에 맞는 예외를 반환해야 한다.
- 문서 트리 조회 결과에서 이동 후 부모/정렬 순서가 올바르게 반영되어야 한다.

### 요청 예시
```json
{
  "targetParentId": "새 부모 문서 ID 또는 null",
  "afterDocumentId": "같은 형제 집합의 앞 문서 ID 또는 null",
  "beforeDocumentId": "같은 형제 집합의 뒤 문서 ID 또는 null"
}
```

### 위치 해석 규칙
- `targetParentId = null`이면 루트 형제 집합으로 이동한다.
- `afterDocumentId`만 있으면 해당 문서 뒤 위치로 해석한다.
- `beforeDocumentId`만 있으면 해당 문서 앞 위치로 해석한다.
- 둘 다 없으면 대상 부모의 마지막 위치로 해석한다.
- 둘 다 있으면 두 문서 사이 위치로 해석한다.

### 권장 정책
- 동일 위치로 이동하는 no-op 요청은 성공으로 처리할 수 있어야 한다.
- no-op 요청은 실제 DB 갱신 없이 성공 응답만 반환할 수 있다.

## 8.5 문서 삭제 / 휴지통 / 복구
### 요구사항
- 문서 기본 삭제 API는 대상 문서, 하위 문서, 각 문서 소속 블록을 즉시 물리 삭제해야 한다.
- 문서 휴지통 이동은 기본 삭제와 분리된 `PATCH /documents/{documentId}/trash` 엔드포인트로 제공해야 한다.
- 문서를 휴지통에 넣을 때 `deletedAt`을 기록해야 한다.
- 문서 휴지통 이동 시 대상 문서의 하위 문서와 각 문서 소속 블록도 함께 휴지통 상태로 전환해야 한다.
- 휴지통 문서는 현재 테스트 기준 `deletedAt + 5분`이 지나면 자동 영구 삭제 대상이 되어야 한다.
- 자동 영구 삭제 시 대상 문서, 하위 문서, 각 문서 소속 블록을 함께 정리해야 한다.
- 휴지통에 들어간 문서는 현재 테스트 기준 5분 이내에는 복구할 수 있어야 한다.
- 복구 시 부모 문서 상태를 함께 검증해야 한다.

### 권장 정책
- 부모 문서가 삭제 상태인 경우, 자식 문서 단독 복구는 실패 처리한다.
- 현재 테스트 기준 5분이 지난 휴지통 문서는 복구 실패 처리한다.
- 자동 삭제 시간은 현재 테스트를 위해 5분으로 두며, 추후 제품 정책에 따라 조정할 수 있어야 한다.

## 8.6 블록 조회
### 요구사항
- 특정 문서의 블록 전체를 조회할 수 있어야 한다.
- 블록은 정렬 순서가 보장되어야 한다.
- 문서 콘텐츠 전체 조회 시 트리 구조로 반환할 수 있어야 한다.

## 8.7 텍스트 블록 생성
### 요구사항
- 사용자는 특정 문서에 TEXT 블록을 추가할 수 있어야 한다.
- 부모 블록 하위에 추가 가능해야 한다.
- 형제 블록 사이 위치를 지정할 수 있어야 한다.
- `content`는 허용된 structured JSON 스키마만 허용한다.

## 8.8 텍스트 블록 수정
### 요구사항
- 블록의 `content` 값을 수정할 수 있어야 한다.
- `content`는 허용된 structured JSON 스키마여야 한다.
- 허용되지 않은 mark, schema, 객체 구조는 거부해야 한다.
- 낡은 버전으로 저장 시 `409 Conflict`를 반환해야 한다.
- 블록 수정은 블록 자신의 내용 또는 메타데이터 변경만 담당한다.
- 블록 이동, 부모 변경, 순서 변경은 블록 수정과 분리된 별도 API에서 처리한다.

## 8.9 블록 삭제
### 요구사항
- 블록 삭제가 가능해야 한다.
- v1에서는 **하위 블록 포함 soft delete**를 기본 정책으로 한다.
- 삭제된 블록은 기본 조회에 포함되지 않아야 한다.

### 권장 정책
- 블록 단위 server restore API는 v1 범위에 포함하지 않는다.
- 같은 브라우저 세션 안의 직전 삭제/수정 복구는 클라이언트 undo/redo로 처리한다.

## 8.10 블록 이동 / 순서 변경
### 요구사항
- 같은 문서 내에서 블록 순서를 바꿀 수 있어야 한다.
- 부모를 변경하여 계층 이동이 가능해야 한다.
- 이동 후 트리 무결성이 깨지면 안 된다.
- 이동 및 재정렬은 트랜잭션으로 처리해야 한다.
- 단일 블록 이동은 drag and drop의 drop 시점에 1회 요청으로 처리할 수 있어야 한다.
- transaction 이동 요청은 `parentRef`, `afterRef`, `beforeRef`, `version`을 기준으로 위치를 해석한다.
- `parentRef`, `afterRef`, `beforeRef`는 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`를 담을 수 있어야 한다.
- 블록 이동 시 `sortKey`, `updatedBy`, `updatedAt`, `version`을 함께 갱신해야 한다.

## 8.11 버전 관리
### 요구사항
- 문서와 블록은 각각 `version` 필드를 가져야 한다.
- `Document.version`은 문서 메타데이터, 문서 내부 block tree, 공개 상태를 포함한 문서 전체 상태의 대표 버전이어야 한다.
- `Block.version`은 개별 block 동시성 검사용 버전으로 유지해야 한다.
- 제목, 아이콘, 커버, 부모, 공개 상태가 실제로 바뀌면 `Document.version`이 증가해야 한다.
- 문서 휴지통 이동, 문서 복구처럼 문서 생명주기가 실제로 바뀌는 작업도 `Document.version` 증가 대상에 포함해야 한다.
- 블록 생성, 수정, 이동, 삭제가 실제로 반영되면 해당 문서의 `Document.version`이 증가해야 한다.
- 한 요청 안에서 문서에 실제 반영된 변경이 하나 이상 있으면 `Document.version`은 정확히 `1`만 증가해야 한다.
- no-op 요청이면 `Document.version`과 `Block.version`을 증가시키지 않아야 한다.
- stale version 요청은 `409 Conflict`를 반환해야 한다.
- 충돌 검출이 필요한 저장 또는 구조 변경 요청은 클라이언트가 기준으로 삼은 `version`을 함께 전달해야 한다.
- 서버는 요청의 `version`과 현재 저장된 `version`을 비교해 stale update를 검출할 수 있어야 한다.

### 버전 역할 분리
- `Document.version`은 프론트가 문서 전체 freshness를 판단하는 기준값이다.
- `Block.version`은 특정 block 하나의 수정 또는 이동 충돌을 검출하는 기준값이다.
- 프론트는 문서 진입 시 문서 조회 응답의 `Document.version`을 기준값으로 저장해야 한다.
- 문서 메타 수정 성공 시에는 응답의 최신 `Document.version`으로 기준값을 갱신해야 한다.
- transaction 저장 성공 시에는 응답의 `documentVersion`으로 기준값을 갱신해야 한다.
- 단건 block API 응답이 최신 `Document.version`을 포함하지 않는 경우 프론트는 문서 조회 응답 또는 transaction 응답의 문서 version을 기준값으로 사용해야 한다.

### 버전 충돌 예시 시나리오
1. 현재 DB의 block version이 `5`이고 content는 `"오늘 회의"`를 표현하는 JSON이다.
2. 사용자 A와 사용자 B가 같은 블록을 조회하고, 둘 다 version `5` 상태의 화면을 보고 잠시 편집만 한 채 저장하지 않는다.
3. 사용자 A는 content를 `"오늘 회의 3시"`가 되도록 바꿔 먼저 저장한다.
4. DB의 block content는 `"오늘 회의 3시"`를 표현하는 최신 JSON이 되고 version은 `6`이 된다.
5. 사용자 B는 여전히 예전 화면 `"오늘 회의"`를 보고 content를 `"오늘 회의 취소"`가 되도록 바꾼 뒤 몇 초 후 저장한다.
6. 사용자 B가 version 없이 저장하면 서버는 최신 row를 다시 읽어 `"오늘 회의 취소"`를 덮어쓸 수 있고, 사용자 A의 `"3시"` 수정이 조용히 사라질 수 있다.
7. 사용자 B가 충돌 검출용 request에 version `5`를 함께 보내면 서버는 현재 DB version `6`과 비교해 stale update로 판단하고 `409 Conflict`를 반환할 수 있다.

### 문서 전체 version 예시 시나리오
1. 사용자가 문서 조회 응답에서 `Document.version = 10`을 받는다.
2. 다른 사용자가 같은 문서의 제목을 수정하면 문서 메타데이터가 바뀌므로 `Document.version`은 `11`이 된다.
3. 다른 사용자가 블록을 하나 수정해도 문서 내부 block tree가 바뀌므로 `Document.version`은 다시 `12`가 된다.
4. 다른 사용자가 공개 상태를 `PRIVATE`에서 `PUBLIC`으로 바꿔도 문서 전체 상태가 바뀌므로 `Document.version`은 다시 `13`이 된다.
5. 반대로 같은 공개 상태를 다시 요청하거나 실제 위치가 바뀌지 않는 block move처럼 no-op이면 `Document.version`은 유지된다.
6. 프론트는 자신이 보관한 기준값과 최신 응답의 `Document.version`이 다르면 현재 화면을 stale 상태로 판단할 수 있어야 한다.

---

## 9. 입력 검증 규칙

## 9.1 공통 검증
- `documentId`, `blockId`, `workspaceId`, `userId`는 UUID 또는 서비스 표준 ID 형식이어야 한다.
- 빈 문자열과 `null`의 허용 범위는 API별로 명확히 정의해야 한다.
- `@Valid` 등 선언적 검증으로 이미 통과한 요청 객체에 대해 중복된 `null`/빈 문자열 보정 로직은 꼭 필요한 경우가 아니면 추가하지 않는다.

## 9.2 워크스페이스 검증
- v1 활성 범위에서 제외한다.
- 기존 Workspace DTO/엔티티 검증 규칙은 추후 재도입 시 다시 적용한다.

## 9.3 문서 검증
- `title` 최대 길이: `255`
- `icon`, `cover`는 허용된 JSON 스키마만 허용
- `targetParentId`가 있으면 활성 문서여야 하고 현재 문서와 같은 사용자 소유 문서여야 한다.
- `afterDocumentId`, `beforeDocumentId`가 있으면 둘 다 활성 형제 문서여야 한다.
- `afterDocumentId`, `beforeDocumentId`를 동시에 받으면 두 문서의 부모가 같아야 하며 서로 인접해야 한다.
- 자기 자신을 부모로 지정하거나 자신의 하위 문서를 부모로 지정하는 요청은 허용하지 않는다.
- v1에서 `icon`, `cover`는 JSON object만 허용한다.
- v1에서 `icon`, `cover`의 최소 허용 스키마는 `{"type":"string","value":"string"}` 형태다.
- `icon.type`, `icon.value`, `cover.type`, `cover.value`는 모두 비어 있지 않은 문자열이어야 한다.
- 배열, 숫자, boolean, plain string, 필수 필드가 누락된 object는 허용하지 않는다.
- 부모 문서는 동일 사용자 소유 문서여야 함
- 자기 자신을 부모로 둘 수 없음
- 순환 참조 금지

## 9.4 블록 검증
- `type`은 현재 `TEXT`만 허용
- `content`는 JSON object여야 함
- `content.format`은 현재 `rich_text`만 허용
- `content.schemaVersion`은 현재 `1`만 허용
- `content.segments`는 배열이어야 하며 비어 있지 않아야 함
- 각 `segment.text`는 문자열이어야 함
- 각 `segment.marks`는 배열이어야 함
- 허용 mark 타입: `bold`, `italic`, `textColor`, `underline`, `strikethrough`
- `textColor.value`는 `#RRGGBB` 형식이어야 함
- 링크, 멘션, inline code, 기타 임의 mark는 v1에서 허용하지 않음
- 블록 전체 plain text 길이 합은 최대 `10,000`
- `page/document` 당 최대 블록 수: `1,000`
- 최대 블록 깊이: `10`
- 다른 문서의 블록을 부모로 둘 수 없음
- 자기 자신을 부모로 둘 수 없음
- 순환 참조 금지

---

## 10. 동시성 및 저장 정책

## 10.1 v1 정책
- 실시간 협업 merge는 보장하지 않는다.
- 에디터 저장 표준 write 경로는 `POST /documents/{documentId}/transactions`를 사용한다.
- autosave와 `Ctrl+S`는 서로 다른 API가 아니라 같은 저장 queue의 flush 트리거다.
- debounce만으로 무한정 저장이 밀리면 안 되며, 장시간 연속 입력 중에도 `max autosave interval` 기준으로 강제 flush가 가능해야 한다.
- 에디터 저장 queue는 클라이언트 로컬에서 관리한다.
- 에디터 queue의 coalescing, 상쇄, 최종 batch 조립은 클라이언트가 담당한다.
- 서버는 클라이언트가 보낸 최종 transaction batch를 검증하고 반영한다.
- 에디터 v1 operation은 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE` 4개만 사용한다.
- `BLOCK_CREATE`는 위치를 항상 확정하고, 필요하면 새 블록의 초기 `content`를 함께 받을 수 있다.
- `BLOCK_CREATE.content`가 없으면 서버는 DB의 `block.content` not null 규칙을 만족시키기 위해 새 `TEXT` 블록에 기본 empty structured content를 저장한다.
- `BLOCK_CREATE.content`가 있으면 서버는 그 값을 새 블록의 초기 `content`로 저장한다.
- 새 temp block에 대한 `BLOCK_CREATE`와 `BLOCK_REPLACE_CONTENT`가 같은 batch에 함께 있으면, 프론트는 flush 전에 이를 `BLOCK_CREATE(content=latestContent)` 하나로 coalescing할 수 있어야 한다.
- `BLOCK_REPLACE_CONTENT`는 range patch가 아니라 block `content` 전체 교체로 처리한다.
- 모든 transaction operation은 블록 참조값으로 `blockRef`를 사용한다.
- `BLOCK_CREATE`의 `blockRef`에는 새 block용 `tempId`를 넣는다.
- `blockRef`는 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 서버가 내려준 실제 `blockId`를 담는다.
- transaction 위치 참조 필드는 `parentRef`, `afterRef`, `beforeRef`를 사용한다.
- `parentRef`, `afterRef`, `beforeRef`도 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`를 담을 수 있어야 한다.
- v1은 temp parent, temp sibling anchor까지 지원해야 한다.
- 서버는 request 순서대로 `tempId -> real blockId` 매핑 컨텍스트를 갱신하면서 `blockRef`, `parentRef`, `afterRef`, `beforeRef`를 모두 해석해야 한다.
- `tempId`는 새 block을 같은 batch 안에서 참조하기 위한 클라이언트 로컬 식별자이며, 서버 영속 ID로 저장하지 않는다.
- 서버는 새 block 생성 시 실제 `blockId`를 새로 발급하고, 성공 응답에서 `tempId -> blockId` 매핑을 반환한다.
- 블록 수정 충돌 판정은 block 단위 낙관적 락을 사용한다.
- stale version이면 `409 Conflict`를 반환한다.
- transaction 실패 정책은 partial apply가 아니라 전체 rollback을 사용한다.
- 충돌 응답에는 충돌 block의 최신 `version`, 최신 `content`를 포함해야 한다.
- 프론트는 최신 block content를 기준으로 로컬 변경을 재적용하거나 사용자에게 충돌 상태를 보여줄 수 있어야 한다.
- 전체 rollback은 서버 반영 기준이며, 프론트는 conflict 시 로컬 draft와 복구에 필요한 pending 상태를 바로 폐기하지 않아야 한다.
- conflict 후 pending 복구는 실패한 batch payload 복원이 아니라, 현재 로컬 문서 상태 기준 재조립을 원칙으로 한다.
- 같은 실패 batch 안의 non-conflict 변경도 서버에는 미반영이므로, 로컬 상태가 유지되고 있으면 다시 pending에 포함될 수 있다.
- 같은 블록 안의 비중첩 수정도 v1에서는 block 단위 충돌로 처리할 수 있다.
- `POST /admin/documents/{documentId}/blocks`, `PATCH /admin/blocks/{blockId}`, `POST /admin/blocks/{blockId}/move`, `DELETE /admin/blocks/{blockId}`는 에디터 표준 저장 경로가 아니라 운영/관리/비에디터 보조 경로로 둘 수 있다.
- 위 4개 admin block API는 path와 HTTP method는 유지하되, request/response 계약과 실제 실행 로직은 `POST /documents/{documentId}/transactions`와 동일한 transaction 모델을 사용해야 한다.
- 각 admin block API는 자기 역할에 맞는 단일 operation 하나만 허용해야 한다.

## 10.2 향후 확장
다음 기능은 v2 이후 별도 서비스 또는 확장 모듈로 분리 가능하다.

- block content operation 모델
- WebSocket
- presence
- cursor sync
- CRDT / OT
- operation log / snapshot 모델

### 권장 로드맵
1. v1: structured content + `transactions` 중심 저장 + block 단위 optimistic lock
2. v2 이후: block content operation 단위 충돌 정보와 재적용 전략 확장
3. 필요 시: WebSocket/presence/cursor sync 기반 협업 모델 검토
4. 필요 시: OT / CRDT 모델 검토

---

## 11. 비기능 요구사항

## 11.1 성능
- 일반적인 문서 조회는 빠르게 응답해야 한다.
- autosave 빈도를 감당할 수 있어야 한다.
- 문서 콘텐츠 전체 조회 시 블록 수 제한 내에서 일관된 응답 시간을 제공해야 한다.

## 11.2 무결성
- 문서 트리 및 블록 트리는 cycle이 없어야 한다.
- 삭제된 문서/블록은 기본 조회에 포함되지 않아야 한다.
- 정렬 키가 중복되더라도 시스템은 일관된 정렬 규칙을 유지해야 한다.
- 블록 이동 및 reorder 시 sibling scope 정합성이 깨지면 안 된다.

## 11.3 감사성
- 누가 문서 또는 블록을 생성/수정했는지 추적 가능해야 한다.
- `createdBy`, `updatedBy`, `createdAt`, `updatedAt`를 유지해야 한다.

## 11.4 보안
- 인증은 외부 서비스(auth-service)에서 받은 identity를 신뢰한다.
- 비로그인 사용자는 gateway 또는 상위 인증 계층에서 차단되므로, 본 서비스는 인증 완료 요청만 처리 대상으로 본다.
- 본 서비스는 `/**` 요청에서 `X-User-Id`가 누락되거나 빈 문자열이면 `401 UNAUTHORIZED`를 반환해야 한다.
- 본 서비스는 직접 공개 엔드포인트로 노출하지 않고, Gateway 경유 트래픽만 수신해야 한다.
- 다른 내부 서비스 호출 시 사용자 위임이 필요하면 `Authorization: Bearer <user access token>`을 전파해야 한다.
- 시스템 내부 호출은 사용자 토큰과 분리된 서비스 전용 토큰을 사용해야 한다.
- 쓰기 요청은 edit 권한이 필요하다.
- HTML은 저장하지 않는다.
- 렌더링 시 XSS 방어는 프론트와 계약으로 명시한다.

## 11.5 운영성
- 문서 soft delete 기반 복구가 가능해야 한다.
- 삭제 및 복구는 감사 로그 또는 운영 로그로 추적 가능해야 한다.
- 장애 복구를 위해 일관된 백업 정책이 가능해야 한다.

---

## 12. API 명세 초안

## 12.1 인증 및 공통 규칙
- 인증 방식: Bearer token 또는 내부 인증 헤더
- Gateway가 인증 성공 후 주입한 내부 신뢰 헤더 `X-User-Id`를 사용한다.
- 외부 공개 API 경로는 Gateway에서 `/v1/**`로 노출하고, 본 서비스 내부 API는 `/v1` 없는 경로를 기준으로 한다.
- `X-User-Id` 누락/빈값 요청은 `UNAUTHORIZED(401)`로 처리한다.
- 요청 추적을 위해 `X-Request-Id`를 수신하며, 누락 시 서버에서 생성해 응답 헤더에 반영한다.
- outbound 호출 인증 모드는 `USER_DELEGATION`, `SERVICE_TO_SERVICE`로 분리한다.
- `USER_DELEGATION`은 inbound `Authorization` Bearer 토큰을 그대로 전파한다.
- `SERVICE_TO_SERVICE`는 `auth.service-token.bearer-token` 설정값을 Bearer 토큰으로 사용한다.
- 응답 포맷: JSON
- 모든 성공/실패 응답은 `documents-api` 모듈의 공통 응답 구조(`GlobalResponse`)를 따른다.
- 비즈니스 성공 코드는 `SuccessCode`로 관리하고, API 오류 응답 코드는 `documents-api` 모듈의 `ErrorCode`로 관리한다.
- `GlobalException`, `ErrorCode`, `BaseResponse`, `GlobalResponse`는 HTTP 응답 계약이므로 `documents-api` 모듈에 둔다.
- 서비스 계층의 비즈니스 예외는 `documents-core` 모듈의 `BusinessException`과 `BusinessErrorCode`로 관리한다.
- API 계층은 `GlobalExceptionHandler`를 통해 `BusinessException`을 `ErrorCode`로 매핑하여 공통 응답으로 변환한다.
- `BusinessErrorCode`와 API `ErrorCode`의 매핑은 enum 이름 일치 규칙을 기본으로 하며, 신규 비즈니스 오류 추가 시 같은 이름의 API 오류 코드를 함께 정의해야 한다.
- `NOT_FOUND` 계열 오류는 `RESOURCE_NOT_FOUND` 같은 범용 이름보다 `WORKSPACE_NOT_FOUND`, `DOCUMENT_NOT_FOUND`, `BLOCK_NOT_FOUND`처럼 도메인별 식별이 가능한 이름을 기본으로 사용한다.
- 오류 메시지는 어떤 도메인 리소스가 실패했는지 직접 드러내야 하며, 같은 HTTP 상태라도 도메인별 오류 코드를 분리할 수 있어야 한다.
- 후속 기능(Document, Block 포함)도 동일한 응답/에러 처리 구조를 사용해야 한다.

### 성공 응답 예시
```json
{
  "httpStatus": "OK",
  "success": true,
  "message": "요청 응답 성공",
  "code": 200,
  "data": {}
}
```

### 실패 응답 예시
```json
{
  "httpStatus": "BAD_REQUEST",
  "success": false,
  "message": "요청 필드 유효성 검사에 실패했습니다.",
  "code": 9016,
  "data": null
}
```

## 12.2 주요 에러 코드
- `UNAUTHORIZED`
- `FORBIDDEN`
- `WORKSPACE_NOT_FOUND`
- `DOCUMENT_NOT_FOUND`
- `BLOCK_NOT_FOUND`
- `SORT_KEY_REBALANCE_REQUIRED`
- `VALIDATION_ERROR`
- `CONFLICT`
- `RATE_LIMITED`
- `INTERNAL_ERROR`

## 12.3 문서 API

### `GET /documents`
로그인한 사용자의 문서 목록 조회.

### `GET /documents/trash`
로그인한 사용자의 휴지통 문서 목록 조회.

### `POST /documents`
로그인한 사용자의 새 문서 생성.

### `GET /documents/{documentId}`
문서 단건 조회.

### `PATCH /documents/{documentId}`
문서 메타데이터 수정.

### `PATCH /documents/{documentId}/visibility`
문서 공개 상태 수정.
- 요청 body는 `visibility`, `version`을 포함해야 한다.
- `visibility`는 `PUBLIC`, `PRIVATE`만 허용해야 한다.
- 요청의 `version`이 현재 `Document.version`과 다르면 `409 Conflict`를 반환해야 한다.
- 상태가 실제로 바뀌면 `Document.version`을 `1` 증가시켜야 한다.
- 같은 상태를 다시 요청하면 no-op으로 처리하고 `Document.version`을 증가시키지 않아야 한다.

### `POST /documents/{documentId}/move`
문서 부모 변경 및 형제 순서 변경.

요청 예시:
```json
{
  "targetParentId": "새 부모 문서 ID 또는 null",
  "afterDocumentId": "같은 형제 집합의 앞 문서 ID 또는 null",
  "beforeDocumentId": "같은 형제 집합의 뒤 문서 ID 또는 null"
}
```

### `DELETE /documents/{documentId}`
문서 soft delete.

### `POST /documents/{documentId}/restore`
문서 복구.

## 12.4 블록 API

- 문서 단위 블록 목록 조회는 문서 API 책임으로 `GET /documents/{documentId}/blocks` 경로를 사용한다.
- 블록 생성, 수정, 이동, 삭제는 관리자 블록 API 책임으로 `/admin/**` 경로를 사용한다.

### `GET /documents/{documentId}/blocks`
문서 내 블록 목록 조회.
- 조회 결과는 soft delete되지 않은 블록만 포함하며 정렬 순서를 보장해야 한다.

### `POST /admin/documents/{documentId}/blocks`
TEXT 블록 생성.
- 이 API는 운영/관리/비에디터 경로에서 사용할 수 있다.
- 에디터 표준 생성/저장 경로는 `transactions`를 사용한다.
- 요청 body는 `POST /documents/{documentId}/transactions`와 같은 transaction request 구조를 사용해야 한다.
- `operations`는 길이 1이어야 하며, 유일한 operation의 `type`은 `BLOCK_CREATE`여야 한다.
- 응답 body는 `DocumentTransactionResponse`와 동일해야 한다.

요청 예시:
```json
{
  "clientId": "admin-api",
  "batchId": "batch-create",
  "operations": [
    {
      "opId": "op-1",
      "type": "BLOCK_CREATE",
      "blockRef": "tmp:block:1",
      "parentRef": null,
      "afterRef": null,
      "beforeRef": null
    }
  ]
}
```

### `PATCH /admin/blocks/{blockId}`
블록 내용 또는 블록 자체 메타데이터 수정.
- 이 API는 운영/관리/비에디터 보조 경로로 둘 수 있다.
- 에디터 표준 본문 저장 경로는 `transactions`를 사용한다.
- 요청 body는 `POST /documents/{documentId}/transactions`와 같은 transaction request 구조를 사용해야 한다.
- `operations`는 길이 1이어야 하며, 유일한 operation의 `type`은 `BLOCK_REPLACE_CONTENT`여야 한다.
- path의 `blockId`와 operation의 `blockRef`는 동일해야 한다.
- 서버는 `blockId`로 소속 `documentId`를 해석한 뒤 transaction과 같은 서비스 경로를 호출해야 한다.
- 응답 body는 `DocumentTransactionResponse`와 동일해야 한다.

내용 수정 예시:
```json
{
  "clientId": "admin-api",
  "batchId": "batch-update",
  "operations": [
    {
      "opId": "op-1",
      "type": "BLOCK_REPLACE_CONTENT",
      "blockRef": "real-block-id",
      "version": 3,
      "content": {
        "format": "rich_text",
        "schemaVersion": 1,
        "segments": [
          {
            "text": "수정된 ",
            "marks": []
          },
          {
            "text": "내용",
            "marks": [
              {
                "type": "bold"
              },
              {
                "type": "textColor",
                "value": "#000000"
              }
            ]
          }
        ]
      }
    }
  ]
}
```

### `POST /admin/blocks/{blockId}/move`
단일 블록 이동.
- 이 API는 운영/관리/비에디터 보조 경로로 둘 수 있다.
- 에디터 표준 이동 경로는 `transactions`를 사용한다.
- 요청 body는 `POST /documents/{documentId}/transactions`와 같은 transaction request 구조를 사용해야 한다.
- `operations`는 길이 1이어야 하며, 유일한 operation의 `type`은 `BLOCK_MOVE`여야 한다.
- path의 `blockId`와 operation의 `blockRef`는 동일해야 한다.
- 서버는 `blockId`로 소속 `documentId`를 해석한 뒤 transaction과 같은 서비스 경로를 호출해야 한다.
- 응답 body는 `DocumentTransactionResponse`와 동일해야 한다.

위치 변경 예시:
```json
{
  "clientId": "admin-api",
  "batchId": "batch-move",
  "operations": [
    {
      "opId": "op-1",
      "type": "BLOCK_MOVE",
      "blockRef": "real-block-id",
      "version": 3,
      "parentRef": "new-parent-block-id",
      "afterRef": "blk-a",
      "beforeRef": "blk-b"
    }
  ]
}
```

### `DELETE /admin/blocks/{blockId}`
블록 soft delete.
- 지정 루트 블록과 하위 블록 subtree를 함께 soft delete 한다.
- 이 API는 명시적 단일 삭제 액션 또는 운영/관리/비에디터 경로에서 사용할 수 있다.
- 에디터 표준 삭제 경로는 `transactions`를 사용한다.
- 요청 body는 `POST /documents/{documentId}/transactions`와 같은 transaction request 구조를 사용해야 한다.
- `operations`는 길이 1이어야 하며, 유일한 operation의 `type`은 `BLOCK_DELETE`여야 한다.
- path의 `blockId`와 operation의 `blockRef`는 동일해야 한다.
- 서버는 `blockId`로 소속 `documentId`를 해석한 뒤 transaction과 같은 서비스 경로를 호출해야 한다.
- 응답 body는 `DocumentTransactionResponse`와 동일해야 한다.

### `POST /documents/{documentId}/transactions`
에디터 생성/저장 batch 반영.
- 에디터의 표준 write 경로다.
- 한 요청에 `BLOCK_CREATE`, `BLOCK_REPLACE_CONTENT`, `BLOCK_MOVE`, `BLOCK_DELETE`를 함께 담을 수 있어야 한다.
- request top-level에는 `clientId`, `batchId`, `operations`를 포함해야 한다.
- 기존 block 수정/이동/삭제 operation은 `version`을 포함해야 한다.
- 모든 transaction operation은 블록 참조 필드로 `blockRef`를 사용해야 한다.
- `BLOCK_CREATE`의 `blockRef`에는 새 block용 `tempId`를 넣어야 한다.
- 위치 참조 필드는 `parentRef`, `afterRef`, `beforeRef`를 사용해야 한다.
- `parentRef`, `afterRef`, `beforeRef`에는 같은 batch 안의 새 block이면 `tempId`, 기존 block이면 실제 `blockId`를 넣을 수 있어야 한다.
- 기존 block 수정/이동/삭제 operation의 `blockRef`에는 서버가 내려준 실제 `blockId`를 넣어야 한다.
- 새 block은 request에서 `blockRef=tempId`로 참조하고, 성공 응답에서 서버가 생성한 실제 `blockId`와 `tempId -> blockId` 매핑을 반환해야 한다.
- 서버는 request 순서대로 `blockRef`, `parentRef`, `afterRef`, `beforeRef`의 temp 참조를 해석할 수 있어야 한다.
- 동시성 검사는 `Document.version`과 block별 `version`을 함께 사용해야 한다.
- 서버는 batch 안에 실제 editor 변경이 하나라도 적용되면 `Document.version`을 증가시키고, 응답에 최신 `documentVersion`을 포함해야 한다.
- `BLOCK_MOVE`, `BLOCK_REPLACE_CONTENT`가 모두 no-op이면 block version과 `documentVersion`을 올리지 않아야 한다.
- 하나의 operation이라도 실패하면 전체 rollback을 적용해야 한다.
- 충돌 응답에는 충돌 block의 최신 `version`, 최신 `content`를 포함해야 한다.

---

## 13. API 상태 코드 기준

| 상태 코드 | 의미 |
|---|---|
| `200` | 조회/수정 성공 |
| `201` | 생성 성공 |
| `204` | 삭제/복구 후 바디 없음 |
| `400` | 요청 형식 오류 |
| `401` | 인증 실패 |
| `403` | 권한 없음 |
| `404` | 리소스 없음 |
| `409` | 동시성 충돌 / 정합성 충돌 |
| `422` | 비즈니스 validation 실패 |
| `429` | Rate limit |
| `500` | 서버 오류 |

---

## 14. 데이터 저장 및 삭제 정책

## 14.1 삭제 정책
- 문서 기본 `DELETE`는 hard delete다.
- 문서 기본 `DELETE`는 대상 문서, 하위 문서, 각 문서 소속 블록을 함께 물리 삭제해야 한다.
- 휴지통 이동은 문서 기본 `DELETE`와 분리된 `PATCH /documents/{documentId}/trash` API로 제공한다.
- 휴지통 상태는 `deletedAt` 설정으로 표현한다.
- 휴지통 이동이 실제로 반영되면 대상 문서와 함께 휴지통 처리된 하위 문서 각각의 `Document.version`도 `1` 증가해야 한다.
- 휴지통에 들어간 문서는 현재 테스트 기준 `deletedAt`으로부터 5분이 지나면 자동 영구 삭제 대상이 된다.
- 자동 영구 삭제는 별도 배치 또는 스케줄러로 수행할 수 있다.
- 자동 영구 삭제 시 대상 문서, 하위 문서, 각 문서 소속 블록을 함께 물리 삭제해야 한다.
- 자동 영구 삭제 시간은 현재 테스트를 위해 5분으로 두며, 추후 정책에 따라 변경할 수 있다.

## 14.2 복구 정책
- v1 복구 대상은 휴지통으로 이동한 문서로 한정한다.
- 휴지통 문서는 보관 시간 안에서만 복구 가능해야 한다.
- 현재 테스트 기준 복구 가능 시간은 `deletedAt` 기준 5분 이내다.
- 문서 복구 시 부모 문서 상태를 검증해야 한다.
- 문서 복구가 실제로 반영되면 대상 문서와 함께 복구된 하위 문서 각각의 `Document.version`도 `1` 증가해야 한다.
- 부모 문서가 삭제 상태면 자식 문서 단독 복구를 제한한다.
- 자동 영구 삭제가 완료된 문서는 복구 대상이 아니다.
- 블록 단위 직전 편집 취소는 브라우저 세션 범위의 undo/redo로 처리한다.

## 14.3 정렬 정책
- 문서 및 블록 정렬은 `sortKey`를 기본으로 한다.
- ordered sibling 집합의 `sortKey` 정책은 lexicographic gap key를 기본으로 한다.
- `sortKey`는 대문자 영숫자(base36)만 사용하는 고정폭 문자열로 저장한다.
- 같은 부모 아래 정렬은 `sortKey ASC` 기준 문자열 정렬로 해석 가능해야 한다.
- 새 항목 생성 시 기본적으로 기존 sibling의 `sortKey`를 일괄 재배치하지 않고, 삽입 위치에 맞는 새 `sortKey`만 발급한다.
- 맨 뒤 추가는 마지막 키에 기본 stride를 더해 발급하고, 사이 삽입은 앞/뒤 키 사이 gap의 중간값을 사용한다.
- 맨 앞 추가는 첫 키에서 기본 stride를 빼고, 여유가 없으면 앞 경계와 첫 키 사이 gap을 사용한다.
- 앞/뒤 gap이 더 이상 없으면 즉시 재정렬을 수행하지 않고 `SORT_KEY_REBALANCE_REQUIRED` 충돌로 처리한다.
- 재정렬(rebalance/compaction)은 별도 관리 작업 또는 후속 reorder API에서 수행한다.
- Block 생성 기능이 이 정책을 먼저 사용하며, Document 정렬도 같은 정책으로 순차 이관한다.

---

## 15. 구현 가이드라인

## 15.1 트랜잭션이 필요한 작업
- 문서 삭제 + 하위 블록 soft delete
- 문서 복구
- 에디터 transaction batch 반영
- 문서 부모 변경

## 15.2 저장 흐름 권장안
### 에디터 transaction
1. 클라이언트는 로컬 queue에서 pending operation을 모은다.
2. debounce 또는 명시적 flush 시 `transactions` 요청을 만든다.
3. 서버는 operation 순서대로 정합성, version, 위치, 삭제 정책을 검증한다.
4. 하나라도 실패하면 전체 rollback 한다.
5. 성공 시 operation별 반영 결과와 새 version, `tempId -> blockId` 매핑을 반환한다.
6. 충돌 시 프론트는 로컬 draft를 유지하고, 현재 로컬 문서 상태 기준으로 pending을 다시 조립한다.

### 단건 블록 생성 보조 경로
1. document 존재 여부 확인
2. parentId가 있으면 같은 document의 block인지 확인
3. afterBlockId / beforeBlockId 정합성 확인
4. 정책에 따라 새 sortKey 계산
5. 빈 TEXT block insert
6. document.updatedAt 및 version 정책 반영
7. commit

### 블록 이동
1. block 존재 여부 확인
2. target parentId가 있으면 같은 document의 block인지 확인
3. afterBlockId / beforeBlockId 정합성 확인
4. 정책에 따라 새 sortKey 계산
5. block.parentId, sortKey, updatedAt, version 갱신
6. commit

### 문서 삭제
1. document 존재 여부 확인
2. document.deletedAt 설정
3. 해당 document의 blocks.deletedAt 일괄 설정
4. 하위 문서 cascade 정책 적용
5. commit

## 15.3 계층 책임 원칙
- Controller는 요청 매핑, 인증 컨텍스트 전달, Service 호출, 공통 응답 포맷 반환만 담당해야 한다.
- 인증 컨텍스트(`X-User-Id`)의 누락/빈값 검증과 request audit 기록(`userId`, `requestId`)은 공통 웹 계층 컴포넌트에서 처리한다.
- Controller는 비즈니스 검증, 리소스 존재 판단, 정합성 검증, 상태 충돌 판단을 직접 구현하지 않는다.
- 리소스 조회 실패, 정합성 위반, 충돌 판단 등 비즈니스 예외는 Service 계층에서 `BusinessException`으로 발생시키고, API 계층은 `GlobalExceptionHandler`를 통해 공통 응답으로 변환한다.
- 후속 API도 같은 원칙을 적용하여 Controller를 얇게 유지해야 한다.
- `@Valid`로 보장된 요청 필드에 대해서는 Service 계층에서 불필요한 `null`/빈 값 파싱과 방어 코드를 최소화한다.
- Service는 유스케이스 오케스트레이션, 트랜잭션 경계, 도메인 규칙 검증에 집중해야 한다.
- 문자열 정규화, 포맷 변환, 직렬화/역직렬화, 정렬 키 포맷 계산처럼 재사용 가능한 기술성 로직은 별도 지원 컴포넌트로 분리해야 한다.
- 현재 구조에서는 과도한 포트/어댑터 추상화보다, 기존 계층 구조를 유지한 채 모듈 내부 유틸/지원 클래스로 분리하는 단순한 설계를 우선한다.
- Mapper는 입출력 모델 변환만 담당하고, JSON codec 등 파싱 세부사항은 별도 지원 객체에 위임해야 한다.

## 15.4 테스트 배치 및 실행 원칙
- 빠른 피드백을 위한 테스트 피라미드를 기본 전략으로 사용한다. 단위 테스트와 slice 테스트를 통합 테스트보다 더 많이 유지해야 한다.
- API 통합 테스트(Controller + Spring MVC + Service + Repository + DB)는 실행 모듈인 `documents-boot`에 둔다.
- `documents-api`는 Controller slice 테스트, 요청/응답 직렬화, `@Valid` 검증, 공통 예외 응답 검증을 위한 빠른 테스트의 기본 위치로 사용한다.
- `documents-core`는 순수 도메인 로직과 서비스 계약 수준의 단위 테스트를 둔다.
- `documents-infrastructure`는 JPA 저장소, 커스텀 쿼리, 영속 구현 검증 테스트와 서비스 구현의 빠른 단위 테스트를 둔다.
- 테스트 의존성은 각 모듈의 테스트 책임에 필요한 최소 범위만 `testImplementation`으로 추가해야 하며, 프로덕션 `implementation` 의존성을 우회하기 위한 용도로 남용하지 않는다.
- 공통 테스트 라이브러리 버전과 좌표는 루트 Gradle 설정에서 관리하고, 각 모듈은 필요한 항목만 선택해서 사용한다.
- 테스트 실행과 의존성 해석은 저장소 루트의 Gradle Wrapper에서 수행하고, 기본 명령은 `./gradlew :대상모듈:test` 형식으로 제한한다.
- API 개발 시 기본 검증 명령은 `./gradlew :documents-boot:test`이며, 필요 시 하위 모듈 테스트를 별도로 추가 실행한다.
- 기능 추가 시 최소한 다음 테스트를 함께 추가해야 한다: Service 단위 테스트, API slice 테스트, 필요한 경우 Repository 테스트, 대표 시나리오 1개 이상의 boot 통합 테스트.
- 모든 테스트 클래스와 테스트 메서드는 한글 `@DisplayName`으로 역할을 명시해야 한다.
- `@DisplayName`은 테스트가 검증하는 행위와 기대 결과가 한눈에 드러나도록 간결하게 작성해야 한다.
- 테스트 메서드의 `@DisplayName`은 성공 검증이면 `성공_${테스트 내용}`, 실패 검증이면 `실패_${테스트 내용}` 형식으로 고정한다.
- `${테스트 내용}`은 문어체로 간결하게 작성하고, 입력 조건과 기대 결과가 바로 드러나야 한다.
- `@Valid`, 경계값, 누락 필드, 잘못된 식별자 형식, 검색/필터 조건 분기, 정렬 조건, 예외 응답 구조를 빠른 테스트 우선으로 커버해야 한다.
- 테스트 커버리지는 장기적으로 라인/브랜치 기준 `80%` 이상을 목표로 하되, 신규 기능은 변경 범위에 대해 우선적으로 높은 커버리지를 확보해야 한다.

---

## 16. 오픈 이슈

다음 항목은 추후 별도 설계 확정이 필요하다.

1. `workspace_members`, `permissions` 테이블 상세 정의
2. 문서 reorder/move 시 fractional indexing 전환 여부 검토
3. 사용자 소셜 로그인 식별자 모델(`providerUserId`) 확정
4. 향후 collaborative editing 도입 시 operations / snapshots / presence 모델 정의
5. 검색 기능이 필요할 경우 별도 인덱싱 전략 수립
6. 첨부 파일/이미지 업로드 도입 시 asset 모델 정의
7. 링크, 멘션, inline code 등 추가 mark 타입의 schema 확장 정책 정의

---

## 17. 최종 요약

`document-service`의 v1 핵심은 다음과 같다.

- 문서는 계층 구조를 가진다.
- 문서 콘텐츠는 ordered block tree로 저장된다.
- 현재 블록 타입은 `TEXT`만 지원한다.
- TEXT 블록 본문은 structured content JSON으로 저장된다.
- v1 mark는 `bold`, `italic`, `textColor`, `underline`, `strikethrough`만 지원한다.
- 문서/블록 생성, 조회, 수정, 삭제, 이동, 재정렬을 지원한다.
- soft delete와 block 단위 optimistic locking을 기본 정책으로 사용한다.
- 인증, 권한 최종 판단, 파일 업로드, 검색, 실시간 협업은 이 서비스 범위 밖이다.
