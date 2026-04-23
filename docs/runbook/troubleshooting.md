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
