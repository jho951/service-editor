package com.documents.service;

import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.support.TextNormalizer;
import io.github.jho951.platform.resource.api.ResourceOwner;
import org.springframework.stereotype.Component;

@Component
public class DocumentsResourceOwnerResolver {

    private static final String OWNER_TYPE = "USER";

    private final TextNormalizer textNormalizer;

    public DocumentsResourceOwnerResolver(TextNormalizer textNormalizer) {
        this.textNormalizer = textNormalizer;
    }

    public ResourceOwner forDocument(Document document) {
        String ownerUserId = textNormalizer.normalizeNullable(document.getCreatedBy());
        if (ownerUserId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
        return new ResourceOwner(OWNER_TYPE, ownerUserId);
    }
}
