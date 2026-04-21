package com.documents.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityActorContextResolver {

    public Set<String> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }

        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority != null && StringUtils.hasText(authority.getAuthority())) {
                roles.add(authority.getAuthority().trim());
            }
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
}
