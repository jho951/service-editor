package com.documents.service;

import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final WorkspaceService workspaceService;

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

        String normalizedActorId = StringUtils.hasText(actorId) ? actorId.trim() : null;

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .parentId(parentId)
                .title(title.trim())
                .iconJson(iconJson)
                .coverJson(coverJson)
                .createdBy(normalizedActorId)
                .updatedBy(normalizedActorId)
                .build();

        return documentRepository.save(document);
    }
}
