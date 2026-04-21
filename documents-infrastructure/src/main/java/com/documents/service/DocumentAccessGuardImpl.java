package com.documents.service;

import java.util.UUID;

import com.documents.domain.Document;
import com.documents.domain.DocumentVisibility;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.support.TextNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DocumentAccessGuardImpl implements DocumentAccessGuard {

    private final DocumentRepository documentRepository;
    private final TextNormalizer textNormalizer;
    private final SecurityActorContextResolver securityActorContextResolver;

    public DocumentAccessGuardImpl(
        DocumentRepository documentRepository,
        TextNormalizer textNormalizer,
        SecurityActorContextResolver securityActorContextResolver
    ) {
        this.documentRepository = documentRepository;
        this.textNormalizer = textNormalizer;
        this.securityActorContextResolver = securityActorContextResolver;
    }

    @Transactional(readOnly = true)
    public Document requireReadable(UUID documentId, String actorId) {
        Document document = findActiveDocument(documentId);
        String normalizedActorId = normalizeActorId(actorId);

        if (canRead(document, normalizedActorId)) {
            return document;
        }
        throw new BusinessException(BusinessErrorCode.FORBIDDEN);
    }

    @Transactional(readOnly = true)
    public Document requireWritable(UUID documentId, String actorId) {
        Document document = findActiveDocument(documentId);
        String normalizedActorId = normalizeActorId(actorId);

        if (canWrite(document, normalizedActorId)) {
            return document;
        }
        throw new BusinessException(BusinessErrorCode.FORBIDDEN);
    }

    private Document findActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }

    private boolean canRead(Document document, String actorId) {
        return securityActorContextResolver.isAdmin()
            || isOwner(document, actorId)
            || document.getVisibility() == DocumentVisibility.PUBLIC;
    }

    private boolean canWrite(Document document, String actorId) {
        return securityActorContextResolver.isAdmin()
            || isOwner(document, actorId);
    }

    private boolean isOwner(Document document, String actorId) {
        return actorId.equals(document.getCreatedBy());
    }

    private String normalizeActorId(String actorId) {
        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId != null) {
            return normalizedActorId;
        }

        String principalName = securityActorContextResolver.currentPrincipalName();
        if (principalName != null) {
            return principalName;
        }
        throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
    }
}
