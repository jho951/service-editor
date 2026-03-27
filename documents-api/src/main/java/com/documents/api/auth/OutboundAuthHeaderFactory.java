package com.documents.api.auth;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.documents.api.code.ErrorCode;
import com.documents.api.exception.GlobalException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboundAuthHeaderFactory {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final ServiceTokenProperties serviceTokenProperties;

    public HttpHeaders createHeaders(OutboundAuthMode mode) {
        return switch (mode) {
            case USER_DELEGATION -> createHeadersForUserDelegation();
            case SERVICE_TO_SERVICE -> createHeadersForServiceToService();
        };
    }

    private HttpHeaders createHeadersForUserDelegation() {
        String authorization = normalize(getRequestHeader(AUTHORIZATION_HEADER));
        if (!isBearerToken(authorization)) {
            throw new GlobalException(ErrorCode.UNAUTHORIZED);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION_HEADER, authorization);

        copyHeaderIfPresent(headers, GatewayAuthContext.USER_ID_HEADER);
        copyHeaderIfPresent(headers, GatewayAuthContext.USER_ROLE_HEADER);
        copyHeaderIfPresent(headers, GatewayAuthContext.REQUEST_ID_HEADER);

        return headers;
    }

    private HttpHeaders createHeadersForServiceToService() {
        String configuredToken = normalize(serviceTokenProperties.getBearerToken());
        if (!StringUtils.hasText(configuredToken)) {
            throw new IllegalStateException("내부 서비스 토큰이 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        if (isBearerToken(configuredToken)) {
            headers.set(AUTHORIZATION_HEADER, configuredToken);
        } else {
            headers.setBearerAuth(configuredToken);
        }

        copyHeaderIfPresent(headers, GatewayAuthContext.REQUEST_ID_HEADER);
        return headers;
    }

    private boolean isBearerToken(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).startsWith("bearer ");
    }

    private void copyHeaderIfPresent(HttpHeaders headers, String headerName) {
        String value = normalize(getRequestHeader(headerName));
        if (StringUtils.hasText(value)) {
            headers.set(headerName, value);
        }
    }

    private String getRequestHeader(String headerName) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader(headerName);
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        return servletRequestAttributes.getRequest();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
