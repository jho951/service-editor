package com.documents.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.support.DocumentSortKeyGenerator;
import com.documents.support.TextNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final WorkspaceService workspaceService;
    private final TextNormalizer textNormalizer;
    private final DocumentSortKeyGenerator documentSortKeyGenerator;

    @Override
    @Transactional
    public Document create(UUID workspaceId, UUID parentId, String title, String iconJson, String coverJson, String actorId) {
        workspaceService.getById(workspaceId);

        if (parentId != null) {
            Document parentDocument = documentRepository.findByIdAndDeletedAtIsNull(parentId)
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

            if (!workspaceId.equals(parentDocument.getWorkspaceId())) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }
        }

        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        String normalizedTitle = textNormalizer.normalizeRequired(title);
        String nextSortKey = documentSortKeyGenerator.genNextSortKey(workspaceId, parentId);

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .parentId(parentId)
                .title(normalizedTitle)
                .iconJson(iconJson)
                .coverJson(coverJson)
                .sortKey(nextSortKey)
                .createdBy(normalizedActorId)
                .updatedBy(normalizedActorId)
                .build();

        return documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getAllByWorkspaceId(UUID workspaceId) {
        workspaceService.getById(workspaceId);
        return documentRepository.findActiveByWorkspaceIdOrderBySortKey(workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Document getById(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public Document update(UUID documentId, String title, String iconJson, String coverJson, UUID parentId, String actorId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }
}
