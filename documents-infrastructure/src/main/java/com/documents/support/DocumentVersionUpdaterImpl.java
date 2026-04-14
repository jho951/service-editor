package com.documents.support;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.service.transaction.DocumentVersionUpdater;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentVersionUpdaterImpl implements DocumentVersionUpdater {

    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public Document increment(UUID documentId, String actorId, LocalDateTime updatedAt) {
        int updatedRowCount = documentRepository.incrementVersion(documentId, actorId, updatedAt);
        if (updatedRowCount != 1) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }
}
