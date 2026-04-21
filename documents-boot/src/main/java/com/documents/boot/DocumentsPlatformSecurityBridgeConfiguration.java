package com.documents.boot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.documents.api.auth.GatewayAuthContext;
import com.documents.api.code.ErrorCode;
import com.documents.api.dto.GlobalResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jho951.platform.security.web.SecurityFailureResponse;
import io.github.jho951.platform.security.web.SecurityFailureResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class DocumentsPlatformSecurityBridgeConfiguration {

    private static final int FORBIDDEN_CODE = 9003;
    private static final int TOO_MANY_REQUESTS_CODE = 9018;

    @Bean
    public DocumentsPlatformHeaderAuthenticationBridgeFilter documentsPlatformHeaderAuthenticationBridgeFilter() {
        return new DocumentsPlatformHeaderAuthenticationBridgeFilter();
    }

    @Bean
    public FilterRegistrationBean<DocumentsPlatformHeaderAuthenticationBridgeFilter> documentsPlatformHeaderAuthenticationBridgeFilterRegistration(
        DocumentsPlatformHeaderAuthenticationBridgeFilter filter
    ) {
        FilterRegistrationBean<DocumentsPlatformHeaderAuthenticationBridgeFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }

    @Bean
    public SecurityFailureResponseWriter securityFailureResponseWriter(ObjectMapper objectMapper) {
        return (request, response, failure) -> writeFailureResponse(objectMapper, response, failure);
    }

    private void writeFailureResponse(
        ObjectMapper objectMapper,
        HttpServletResponse response,
        SecurityFailureResponse failure
    ) throws IOException {
        response.setStatus(failure.status());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), toResponseEnvelope(failure));
    }

    private GlobalResponse<Void> toResponseEnvelope(SecurityFailureResponse failure) {
        return switch (failure.status()) {
            case 401 -> GlobalResponse.fail(ErrorCode.UNAUTHORIZED);
            case 403 -> new GlobalResponse<>(
                org.springframework.http.HttpStatus.FORBIDDEN,
                false,
                "요청을 수행할 권한이 없습니다.",
                FORBIDDEN_CODE,
                null
            );
            case 429 -> new GlobalResponse<>(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                false,
                "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
                TOO_MANY_REQUESTS_CODE,
                null
            );
            default -> new GlobalResponse<>(
                org.springframework.http.HttpStatus.valueOf(failure.status()),
                false,
                StringUtils.hasText(failure.message()) ? failure.message() : ErrorCode.FAIL.getMessage(),
                ErrorCode.FAIL.getCode(),
                null
            );
        };
    }

    static final class DocumentsPlatformHeaderAuthenticationBridgeFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
        ) throws ServletException, IOException {
            String requestId = normalize(request.getHeader(GatewayAuthContext.REQUEST_ID_HEADER));
            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
            }

            request.setAttribute(GatewayAuthContext.REQUEST_ID_ATTRIBUTE, requestId);
            response.setHeader(GatewayAuthContext.REQUEST_ID_HEADER, requestId);

            String userId = normalize(request.getHeader(GatewayAuthContext.USER_ID_HEADER));
            if (userId != null) {
                request.setAttribute(GatewayAuthContext.REQUEST_USER_ID_ATTRIBUTE, userId);
            }

            Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
            boolean authenticationBridged = false;

            if (previousAuthentication == null && userId != null) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(createAuthentication(request, userId));
                SecurityContextHolder.setContext(context);
                authenticationBridged = true;
            }

            try {
                filterChain.doFilter(request, response);
            } finally {
                if (authenticationBridged) {
                    SecurityContextHolder.clearContext();
                }
            }
        }

        private Authentication createAuthentication(HttpServletRequest request, String userId) {
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                userId,
                null,
                authorities(request)
            );
            authentication.setDetails(request);
            return authentication;
        }

        private List<GrantedAuthority> authorities(HttpServletRequest request) {
            String userRoleHeader = normalize(request.getHeader(GatewayAuthContext.USER_ROLE_HEADER));
            if (userRoleHeader == null) {
                return List.of();
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            for (String token : userRoleHeader.split(",")) {
                String authority = normalize(token);
                if (authority != null) {
                    authorities.add(new SimpleGrantedAuthority(authority));
                }
            }
            return List.copyOf(authorities);
        }

        private String normalize(String value) {
            return StringUtils.hasText(value) ? value.trim() : null;
        }
    }
}
