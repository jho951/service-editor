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

---

## 1. 문서 개요

`document-service`는 Notion 유사 문서 시스템에서 **문서 메타데이터와 문서 콘텐츠 블록 구조를 저장, 조회, 수정, 삭제**하는 서비스를 의미한다.

이 서비스는 다음 두 가지를 함께 소유한다.

1. **문서(Document) 계층 구조**
2. **문서 내부 블록(Block) 트리 구조**

현재 단계의 목표는 **Notion형 문서 서비스의 최소 기능(MVP)** 을 안정적으로 제공하는 것이다.
다만 블록 표현력은 축소하여, **v1에서는 `TEXT` 타입 블록 1종만 지원**한다.

핵심 요구사항은 다음 한 문장으로 요약된다.

> **문서는 계층 구조를 가지며, 각 문서의 콘텐츠는 ordered block tree로 저장된다. 현재 block.type은 `TEXT`만 지원하고, block.content는 plain string이다.**

---

## 2. 서비스 목표

### 2.1 비즈니스 목표
- 사용자가 워크스페이스 내에서 문서를 생성하고 관리할 수 있어야 한다.
- 각 문서는 부모-자식 관계를 갖는 계층 구조를 형성할 수 있어야 한다.
- 각 문서의 본문은 블록 트리 구조로 저장되어야 한다.
- 블록은 생성, 수정, 삭제, 이동, 재정렬이 가능해야 한다.
- 자동 저장(autosave) 시나리오를 감당할 수 있어야 한다.
- v1에서는 복잡한 리치 텍스트 편집 대신 **plain text block editor** 수준으로 범위를 제한한다.

### 2.2 기술적 목표
- soft delete를 기본 삭제 전략으로 사용한다.
- 문서 및 블록 수정 시 낙관적 락(optimistic locking)을 적용한다.
- 문서/블록 트리의 무결성을 보장한다.
- 향후 리치 텍스트, 협업 편집, 파일 블록 등으로 확장 가능해야 한다.

---

## 3. 서비스 경계

## 3.1 document-service가 소유하는 것
- 워크스페이스 내 문서 메타데이터
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

---

## 4. MVP 범위

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
- `block.content`는 plain string만 허용한다.
- HTML, rich text span, JSON fragment, 파일 객체는 허용하지 않는다.

---

## 5. 현재 제외 범위 (Out of Scope)

다음 기능은 v1 범위에서 제외한다.

- 이미지 블록
- 파일 블록
- 체크박스 블록
- 코드 블록
- 데이터베이스 블록
- 멘션
- 리치 텍스트 span 스타일링
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

## 6.2 Document

```text
Document {
  id: string
  workspaceId: string
  parentId: string | null
  title: string
  icon: json | null
  cover: json | null
  sortKey: string | null
  version: number
  createdBy: string | null
  updatedBy: string | null
  createdAt: datetime
  updatedAt: datetime
  deletedAt: datetime | null
}
```

### 설명
- `workspaceId`: 문서가 속한 워크스페이스
- `parentId`: 상위 문서 ID. `null`이면 루트 문서
- `sortKey`: 같은 부모 아래 문서 순서 정렬용 키
- `version`: 낙관적 락용 버전
- `deletedAt`: soft delete 시각

## 6.3 Block

```text
Block {
  id: string
  documentId: string
  parentId: string | null
  type: "TEXT"
  text: string
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
- `documentId`: 블록이 속한 문서
- `parentId`: 상위 블록 ID. `null`이면 문서 루트 블록
- `sortKey`: 같은 부모 아래 블록 순서 정렬용 키
- `text`: TEXT 블록의 본문. plain string only
- `version`: 낙관적 락용 버전

---

## 7. 도메인 무결성 규칙

## 7.1 문서 규칙
1. `parentId IS NULL`이면 루트 문서다.
2. 하위 문서는 반드시 같은 `workspaceId` 내 부모 문서를 가져야 한다.
3. soft delete된 문서는 기본 조회에서 제외한다.
4. 문서를 삭제하면 해당 문서의 블록도 함께 soft delete 처리해야 한다.
5. 하위 문서 cascade delete 여부는 제품 정책으로 확정해야 하나, v1에서는 **하위 문서까지 soft delete**를 권장한다.
6. 자기 자신을 부모 문서로 둘 수 없다.
7. 순환 참조(cycle)는 허용하지 않는다.

## 7.2 블록 규칙
1. `parentId IS NULL`이면 문서 루트 블록이다.
2. 블록의 부모 블록은 반드시 같은 `documentId`를 가져야 한다.
3. 같은 형제 집합(sibling scope) 내 활성 블록의 `sortKey`는 유일해야 한다.
4. soft delete된 블록은 기본 렌더링에서 제외한다.
5. 블록 이동은 `parentId`, `sortKey`, `updatedBy`, `updatedAt`, `version`을 한 트랜잭션에서 갱신해야 한다.
6. 자기 자신을 부모 블록으로 둘 수 없다.
7. 순환 참조(cycle)는 허용하지 않는다.
8. 다른 문서의 블록을 부모로 둘 수 없다.

## 7.3 사용자 참조 규칙
1. 쓰기 작업은 인증된 사용자만 수행할 수 있다.
2. 쓰기 작업에는 edit 권한이 필요하다.
3. 비활성 사용자에 대한 처리 정책은 auth-service / permission-service 정책을 따른다.

---

## 8. 기능 요구사항

## 8.1 문서 조회
### 요구사항
- 특정 워크스페이스의 문서 목록을 조회할 수 있어야 한다.
- 특정 문서를 단건 조회할 수 있어야 한다.
- 특정 문서의 콘텐츠를 블록 트리와 함께 조회할 수 있어야 한다.
- 기본 조회는 soft delete되지 않은 리소스만 포함해야 한다.

### 결과 조건
- 문서 계층 구조 정보가 유지되어야 한다.
- 문서 버전 정보가 포함될 수 있어야 한다.
- 문서 콘텐츠 조회 시 블록 순서가 보장되어야 한다.

## 8.2 문서 생성
### 요구사항
- 사용자는 워크스페이스 내에 새 문서를 생성할 수 있어야 한다.
- 루트 문서 또는 특정 부모 문서 하위로 생성 가능해야 한다.
- 문서 제목, 아이콘, 커버를 지정할 수 있어야 한다.

### 결과 조건
- 생성된 문서는 지정된 부모 및 워크스페이스와 정합성을 가져야 한다.
- 생성 직후 버전 정보가 초기화되어야 한다.

## 8.3 문서 수정
### 요구사항
- 문서 제목, 아이콘, 커버, 부모 문서를 수정할 수 있어야 한다.
- 부모 변경 시 같은 워크스페이스 내 부모인지 검증해야 한다.
- 낡은 버전으로 수정 요청 시 `409 Conflict`를 반환해야 한다.

## 8.4 문서 삭제 및 복구
### 요구사항
- 문서 삭제는 soft delete로 처리해야 한다.
- 문서 삭제 시 해당 문서의 모든 블록도 soft delete 처리해야 한다.
- 삭제된 문서는 복구할 수 있어야 한다.
- 복구 시 부모 문서 상태를 함께 검증해야 한다.

### 권장 정책
- 부모 문서가 삭제 상태인 경우, 자식 문서 단독 복구는 실패 처리한다.

## 8.5 블록 조회
### 요구사항
- 특정 문서의 블록 전체를 조회할 수 있어야 한다.
- 특정 부모 블록 하위의 자식 블록만 조회할 수 있어야 한다.
- 블록은 정렬 순서가 보장되어야 한다.
- 문서 콘텐츠 전체 조회 시 트리 구조로 반환할 수 있어야 한다.

## 8.6 텍스트 블록 생성
### 요구사항
- 사용자는 특정 문서에 TEXT 블록을 추가할 수 있어야 한다.
- 부모 블록 하위에 추가 가능해야 한다.
- 형제 블록 사이 위치를 지정할 수 있어야 한다.
- `text`는 문자열만 허용한다.

## 8.7 텍스트 블록 수정
### 요구사항
- 블록의 `text` 값을 수정할 수 있어야 한다.
- `text`는 plain string이어야 한다.
- HTML, JSON 구조, 파일 객체는 허용하지 않는다.
- 낡은 버전으로 저장 시 `409 Conflict`를 반환해야 한다.

## 8.8 블록 삭제
### 요구사항
- 블록 삭제가 가능해야 한다.
- v1에서는 **하위 블록 포함 soft delete**를 기본 정책으로 한다.
- 삭제된 블록은 기본 조회에 포함되지 않아야 한다.

## 8.9 블록 이동 / 순서 변경
### 요구사항
- 같은 문서 내에서 블록 순서를 바꿀 수 있어야 한다.
- 부모를 변경하여 계층 이동이 가능해야 한다.
- 이동 후 트리 무결성이 깨지면 안 된다.
- 이동 및 재정렬은 트랜잭션으로 처리해야 한다.

## 8.10 버전 관리
### 요구사항
- 문서와 블록은 각각 `version` 필드를 가져야 한다.
- 수정 시 버전이 증가해야 한다.
- stale version 요청은 `409 Conflict`를 반환해야 한다.

---

## 9. 입력 검증 규칙

## 9.1 공통 검증
- `documentId`, `blockId`, `workspaceId`, `userId`는 UUID 또는 서비스 표준 ID 형식이어야 한다.
- 빈 문자열과 `null`의 허용 범위는 API별로 명확히 정의해야 한다.

## 9.2 문서 검증
- `title` 최대 길이: `255`
- `icon`, `cover`는 허용된 JSON 스키마만 허용
- 부모 문서는 동일 워크스페이스 내 존재해야 함
- 자기 자신을 부모로 둘 수 없음
- 순환 참조 금지

## 9.3 블록 검증
- `type`은 현재 `TEXT`만 허용
- `text`는 문자열이어야 함
- `text` 최대 길이: `10,000`
- `page/document` 당 최대 블록 수: `1,000`
- 최대 블록 깊이: `10`
- 다른 문서의 블록을 부모로 둘 수 없음
- 자기 자신을 부모로 둘 수 없음
- 순환 참조 금지

---

## 10. 동시성 및 저장 정책

## 10.1 v1 정책
- 실시간 협업 merge는 보장하지 않는다.
- 낙관적 락을 사용한다.
- stale version이면 `409 Conflict`를 반환한다.
- 프론트는 최신 데이터를 다시 조회한 후 재적용한다.

## 10.2 향후 확장
다음 기능은 v2 이후 별도 서비스 또는 확장 모듈로 분리 가능하다.

- WebSocket
- presence
- cursor sync
- CRDT / OT
- operation log / snapshot 모델

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
- 쓰기 요청은 edit 권한이 필요하다.
- HTML은 저장하지 않는다.
- 렌더링 시 XSS 방어는 프론트와 계약으로 명시한다.

## 11.5 운영성
- soft delete 기반 복구가 가능해야 한다.
- 삭제 및 복구는 감사 로그 또는 운영 로그로 추적 가능해야 한다.
- 장애 복구를 위해 일관된 백업 정책이 가능해야 한다.

---

## 12. API 명세 초안

## 12.1 인증 및 공통 규칙
- 인증 방식: Bearer token 또는 내부 인증 헤더
- 응답 포맷: JSON
- 에러 응답은 표준 구조를 따른다.

### 성공 응답 예시
```json
{
  "data": {}
}
```

### 실패 응답 예시
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "title is required",
    "details": {
      "field": "title"
    }
  }
}
```

## 12.2 주요 에러 코드
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `CONFLICT`
- `RATE_LIMITED`
- `INTERNAL_ERROR`

## 12.3 문서 API

### `GET /v1/workspaces/{workspaceId}/documents`
워크스페이스 내 문서 목록 조회.

### `POST /v1/workspaces/{workspaceId}/documents`
새 문서 생성.

### `GET /v1/documents/{documentId}`
문서 단건 조회.

### `PATCH /v1/documents/{documentId}`
문서 수정.

### `DELETE /v1/documents/{documentId}`
문서 soft delete.

### `POST /v1/documents/{documentId}/restore`
문서 복구.

### `GET /v1/documents/{documentId}/content`
문서 메타데이터와 활성 블록 전체를 한 번에 조회.

## 12.4 블록 API

### `GET /v1/documents/{documentId}/blocks`
문서 내 블록 목록 조회.

### `POST /v1/documents/{documentId}/blocks`
TEXT 블록 생성.

요청 예시:
```json
{
  "parentId": null,
  "type": "TEXT",
  "text": "새 블록",
  "afterBlockId": null,
  "beforeBlockId": null
}
```

### `PATCH /v1/blocks/{blockId}`
블록 내용 수정 또는 이동.

내용 수정 예시:
```json
{
  "text": "수정된 내용",
  "version": 3
}
```

위치 변경 예시:
```json
{
  "parentId": "new-parent-block-id",
  "afterBlockId": "blk-a",
  "beforeBlockId": "blk-b",
  "version": 3
}
```

### `POST /v1/documents/{documentId}/blocks/reorder`
여러 블록 reorder 전용 API.

### `DELETE /v1/blocks/{blockId}`
블록 soft delete.

### `POST /v1/blocks/{blockId}/restore`
블록 복구.

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
- API의 `DELETE`는 물리 삭제가 아니라 soft delete다.
- soft delete는 `deletedAt` 설정으로 표현한다.
- 물리 삭제는 별도 배치 또는 운영 툴로만 수행한다.

## 14.2 복구 정책
- soft delete 후 일정 기간 내 복구 가능해야 한다.
- 복구 시 부모 문서/블록 상태를 검증해야 한다.
- 부모가 삭제 상태면 단독 복구를 제한한다.

## 14.3 정렬 정책
- 문서 및 블록 정렬은 `sortKey`를 기본으로 한다.
- 중간 삽입이 자주 발생하는 경우 fractional indexing 또는 lexicographic rank 전략을 사용할 수 있다.
- 초기 구현은 단순 정수 기반 sort 전략으로 시작할 수 있다.

---

## 15. 구현 가이드라인

## 15.1 트랜잭션이 필요한 작업
- 문서 삭제 + 하위 블록 soft delete
- 문서 복구
- 블록 이동
- 블록 reorder
- 문서 부모 변경
- 블록 복구

## 15.2 저장 흐름 권장안
### 블록 생성
1. document 존재 여부 확인
2. parentId가 있으면 같은 document의 block인지 확인
3. afterBlockId / beforeBlockId 정합성 확인
4. 새 sortKey 계산
5. block insert
6. document.updatedAt 및 version 정책 반영
7. commit

### 문서 삭제
1. document 존재 여부 확인
2. document.deletedAt 설정
3. 해당 document의 blocks.deletedAt 일괄 설정
4. 하위 문서 cascade 정책 적용
5. commit

---

## 16. 오픈 이슈

다음 항목은 추후 별도 설계 확정이 필요하다.

1. `workspace`, `workspace_members`, `permissions` 테이블 상세 정의
2. 문서 정렬을 위한 `documents.sortKey` 확정
3. 사용자 소셜 로그인 식별자 모델(`providerUserId`) 확정
4. 향후 collaborative editing 도입 시 operations / snapshots / presence 모델 정의
5. 검색 기능이 필요할 경우 별도 인덱싱 전략 수립
6. 첨부 파일/이미지 업로드 도입 시 asset 모델 정의

---

## 17. 최종 요약

`document-service`의 v1 핵심은 다음과 같다.

- 문서는 계층 구조를 가진다.
- 문서 콘텐츠는 ordered block tree로 저장된다.
- 현재 블록 타입은 `TEXT`만 지원한다.
- TEXT 블록 본문은 plain string만 허용한다.
- 문서/블록 생성, 조회, 수정, 삭제, 이동, 재정렬을 지원한다.
- soft delete와 optimistic locking을 기본 정책으로 사용한다.
- 인증, 권한 최종 판단, 파일 업로드, 검색, 실시간 협업은 이 서비스 범위 밖이다.
