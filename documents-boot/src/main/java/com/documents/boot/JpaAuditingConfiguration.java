package com.documents.boot;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "requestAuditorAware")
public class JpaAuditingConfiguration {

    private static final String REQUEST_USER_ID_ATTRIBUTE = "gatewayAuthenticatedUserId";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    public AuditorAware<String> requestAuditorAware() {
        return () -> currentRequest()
            .map(this::resolveAuditor)
            .filter(StringUtils::hasText)
            .map(String::trim);
    }

    private Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    private String resolveAuditor(HttpServletRequest request) {
        Object userId = request.getAttribute(REQUEST_USER_ID_ATTRIBUTE);
        if (userId instanceof String stringUserId) {
            return stringUserId;
        }

        if (request.getUserPrincipal() != null && StringUtils.hasText(request.getUserPrincipal().getName())) {
            return request.getUserPrincipal().getName();
        }

        return request.getHeader(USER_ID_HEADER);
    }
}
