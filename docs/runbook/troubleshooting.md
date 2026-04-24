# Troubleshooting

## `/v1/documents`가 `401 invalid_token`을 반환함

증상:

- Gateway를 거친 `POST /v1/documents`, `GET /v1/documents/**` 요청이 `401 Unauthorized`를 반환합니다.
- 응답 헤더의 `www-authenticate`에 `Bearer error="invalid_token"`이 보입니다.
- 메시지에 `The iss claim is not valid` 또는 `aud claim is not valid`가 포함될 수 있습니다.

원인:

- Editor 서버는 브라우저 쿠키를 직접 검증하지 않습니다.
- Gateway가 보호 요청에 대해 `Authorization` 내부 JWT를 다시 주입합니다.
- Editor 서버가 여전히 이전 issuer/audience 또는 다른 shared secret을 기대하면 JWT 검증 단계에서 거부합니다.

확인:

- Gateway: `GATEWAY_INTERNAL_JWT_SHARED_SECRET`, `GATEWAY_INTERNAL_JWT_ISSUER`, `GATEWAY_INTERNAL_JWT_AUDIENCE`
- Editor: `PLATFORM_SECURITY_JWT_SECRET`, `PLATFORM_SECURITY_JWT_ISSUER`, `PLATFORM_SECURITY_JWT_AUDIENCE`
- 현재 계약이 `issuer=api-gateway`, `audience=internal-services`인지 확인합니다.

해결:

- Editor 서버는 Gateway 내부 JWT 계약을 기준으로 검증합니다.
- 운영에서는 Gateway와 Editor의 shared secret을 같은 값으로 맞춥니다.
- `authz-service` caller proof 용 `aud=authz-service` 토큰은 Editor 서버 인증 토큰으로 사용하지 않습니다.

## 브라우저 쿠키는 있는데 Editor만 401임

증상:

- 로그인은 되어 있고 다른 auth 경로는 정상인데 문서 API만 실패합니다.

원인:

- 브라우저의 cookie session 성공과 downstream JWT 검증 성공은 다른 단계입니다.
- Gateway는 세션을 확인한 뒤 내부 JWT로 정규화해서 Editor에 전달합니다.
- 따라서 cookie가 정상이어도 Editor의 내부 JWT 설정이 틀리면 401이 납니다.

확인:

- Gateway가 `X-User-Id`와 내부 `Authorization`을 downstream에 재주입하는지 확인합니다.
- Editor 앱 설정이 최신 계약을 보도록 배포됐는지 확인합니다.

해결:

- 세션 쿠키 문제와 내부 JWT 문제를 분리해서 확인합니다.
- 먼저 Gateway 인증 성공 여부를 보고, 다음으로 Editor JWT 계약 일치를 확인합니다.

## EC2 Compose와 ECS/Fargate 중 무엇을 쓸지

editor-service는 두 방식을 모두 검토했습니다. 구현 이력과 대표 코드 조각은 service-contract의 `shared/deployment-topologies.md`에 남깁니다.

`EC2 + Docker Compose`가 맞는 경우:

- 문서 API와 MySQL, 파일 경로, exporter를 한 host에서 빠르게 묶어 검증해야 합니다.
- 운영 단순성이 무중단보다 우선입니다.

`ECS/Fargate + CodeDeploy`가 맞는 경우:

- `/v1/documents/**`와 editor save API를 배포 중에도 끊으면 안 됩니다.
- 새 task set의 health/readiness를 본 뒤 트래픽을 옮겨야 합니다.
- rollback을 컨테이너 재기동이 아니라 task definition revision 기준으로 관리해야 합니다.

현재 운영 기본값:

- 현재 Free Tier 계정에서는 `단일 EC2 + docker compose`를 실제 배포 기본값으로 둡니다.
- editor-service는 gateway 뒤 same-host compose network로 연결하고, 파일 저장 경로도 단일 host 기준으로 운영합니다.
- 비용 제약이 해제되면 `ECS/Fargate + CodeDeploy blue/green`으로 승격합니다.
- MySQL runtime은 애플리케이션과 분리하는 쪽을 우선합니다.
- `docker/prod/compose.yml`은 로컬/임시 검증과 fallback reference로만 취급합니다.
