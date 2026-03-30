# ADR 019: Docker 런타임을 사설 네트워크와 공유 서비스 네트워크로 분리한다

## 상태

채택됨

## 배경

- `document-service`의 Docker 실행은 개발용 DB와 애플리케이션을 함께 띄우는 형태다.
- 이전 구성은 `mysql`과 `app`을 호스트 포트에 직접 노출해, 컨테이너 내부 통신과 외부 접근 경계가 느슨했다.
- 같은 `service-backbone-shared` 네트워크를 쓰는 다른 서비스와 연결할 수 있어야 하므로, 서비스 식별 가능한 별칭도 필요하다.

## 결정

- `mysql`과 `app`은 호스트 포트를 직접 publish하지 않는다.
- `mysql`과 `app`은 `documents-private` 사설 브리지 네트워크에 함께 연결한다.
- `app`은 외부 공유 네트워크 `service-backbone-shared`에도 연결하고, 서비스 별칭은 `documents-service`를 사용한다.
- 공유 네트워크 이름은 `SERVICE_SHARED_NETWORK` 환경변수로 덮어쓸 수 있고, 기본값은 `service-backbone-shared`다.

## 영향

- 장점:
  - 호스트 포트 노출을 줄여 런타임 경계가 명확해진다.
  - 다른 서비스가 공유 네트워크에서 `documents-service`로 안정적으로 접근할 수 있다.
  - dev/prod compose가 같은 연결 정책을 공유해 운영 차이가 줄어든다.
- 단점:
  - Docker compose만으로는 로컬 호스트에서 직접 접속하기 어렵다.
  - 공유 네트워크가 없으면 외부 서비스 연동이 실패하므로 선행 준비가 필요하다.
