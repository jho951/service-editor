package com.documents.service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class SecurityActorContextResolver {
    private static final String USER_ROLE_HEADER = "X-User-Role";

    public Set<String> currentRoles() {
        Set<String> roles = new LinkedHashSet<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if (authority != null && StringUtils.hasText(authority.getAuthority())) {
                    roles.add(authority.getAuthority().trim());
                }
            }
        }

        currentRequest().ifPresent(request -> {
            String roleHeader = request.getHeader(USER_ROLE_HEADER);
            if (!StringUtils.hasText(roleHeader)) {
                return;
            }
            for (String token : roleHeader.split(",")) {
                if (StringUtils.hasText(token)) {
                    roles.add(token.trim());
                }
            }
        });

        if (roles.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return currentRoles().stream().anyMatch(this::isAdminRole);
    }

    public String currentPrincipalName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return null;
        }
        return authentication.getName().trim();
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "ROLE_ADMIN".equals(role);
    }

    private Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }
}
