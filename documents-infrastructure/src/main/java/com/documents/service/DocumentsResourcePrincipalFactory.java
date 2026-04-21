package com.documents.service;

import java.util.Set;

import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.support.TextNormalizer;
import io.github.jho951.platform.resource.api.ResourceOwner;
import io.github.jho951.platform.resource.api.ResourcePrincipal;
import org.springframework.stereotype.Component;

@Component
public class DocumentsResourcePrincipalFactory {

    private static final String USER_TYPE = "USER";
    private static final String SYSTEM_TYPE = "SYSTEM";
    private static final String SYSTEM_ID = "documents-app";

    private final TextNormalizer textNormalizer;
    private final SecurityActorContextResolver securityActorContextResolver;

    public DocumentsResourcePrincipalFactory(
        TextNormalizer textNormalizer,
        SecurityActorContextResolver securityActorContextResolver
    ) {
        this.textNormalizer = textNormalizer;
        this.securityActorContextResolver = securityActorContextResolver;
    }

    public ResourcePrincipal create(String actorId) {
        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId == null) {
            normalizedActorId = securityActorContextResolver.currentPrincipalName();
        }
        if (normalizedActorId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        return new ResourcePrincipal(
            new ResourceOwner(USER_TYPE, normalizedActorId),
            securityActorContextResolver.currentRoles(),
            Set.of()
        );
    }

    public ResourcePrincipal systemPrincipal() {
        return new ResourcePrincipal(
            new ResourceOwner(SYSTEM_TYPE, SYSTEM_ID),
            Set.of("ADMIN"),
            Set.of()
        );
    }
}
