# Contributing Guide

### 해당 문서는 코드 기여 시 지켜야 할 기본 원칙과 규칙을 설명합니다.

---

## 🧭 Project 목표

### 재사용 가능한 인증 라이브러리를 목표로 하며 다음 원칙을 지향합니다.

- 서비스 종속 로직 금지
- 인터페이스(SPI) 중심 설계
- 인증과 사용자 저장소(User DB)의 명확한 분리
- Spring Boot AutoConfiguration 기반 확장성

---

## 📦 Module Responsibility

### ❗ `auth-core`에는 **Spring, JWT, DB 의존성 추가 금지**

| Module | Responsibility            |
|--------|---------------------------|
| contract | 외부에 노출되는 모델/예외 계약          |
| core   | 인증 도메인 로직 (비즈니스 규칙)       |
| spi    | 서비스별 구현이 필요한 인터페이스        |
| starter | Spring Boot 연동 + AutoConfiguration |
| common | 공통 유틸리티                    |



---

## 🧑‍💻 Coding Rules

### Java
- Java 17+
- 불변 객체 우선
- 생성자에서 유효성 검증
- `Optional`은 반환용으로만 사용

### Exception
- `RuntimeException` 직접 사용 ❌
- `AuthException + ErrorCode` 사용 ⭕

---

## ⚙️ Configuration Rules

- 모든 설정은 `@ConfigurationProperties` 사용
- prefix는 `auth.*` 하위로 제한
- yml에 민감 정보(secret) 직접 작성 금지 ( ***환경 변수 사용 권장*** )
---

## 🔌 SPI 규칙

- SPI 인터페이스는 반드시 `auth-spi`에 위치
- 구현체는 **절대 auth 모듈 안에 두지 않음**
- 구현 예시는 테스트 또는 README에만 포함

---

## 🧪 Testing

- core 로직은 단위 테스트 필수
- AutoConfiguration은 context load 테스트 권장
- 외부 시스템(DB, Redis 등) 의존 테스트 금지

---

## 📝 Commit 전략 (권장)

### type
- `chore` : 환경 설정, 초기 세팅
- `docs` : 문서 작성 및 수정
- `feat` : 기능/로직 구현
- `test` : 테스트 코드 추가/수정
- `refactor` : 리팩토링 및 구조 개선

### scope
- 문제 디렉토리명 (예: `problem-01`, `problem-02`)

### 예시
- `docs(problem-01): 요구사항 및 예외 상황 정리`
- `feat(problem-02): 핵심 로직 구현`
- `test(problem-01): 정상/경계/예외 테스트 추가`

---

## 📬 Issues & PR

- 기능 추가 전 Issue 등록
- PR에는 반드시 변경 목적과 영향 범위 명시
- 공용 API 변경 시 Breaking Change 여부 명확히 표시

---


## 🚀 추가 확장 고려 사항

### 1. 새로운 인증 수단 추가 (예: OAuth2, Passkey)

### 2. 토큰 저장소 확장 (예: Redis, DB, In-memory)

### 3. 보안 정책 강화

--- 

## 기여해주셔서 감사합니다.
