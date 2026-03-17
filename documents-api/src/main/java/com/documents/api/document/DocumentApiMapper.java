package com.documents.api.document;

import com.documents.api.document.dto.CreateDocumentRequest;
import com.documents.api.document.dto.DocumentResponse;
import com.documents.api.document.support.DocumentJsonCodec;
import com.documents.domain.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentApiMapper {

    private final DocumentJsonCodec documentJsonCodec;

    public String serializeIcon(CreateDocumentRequest request) {
        return documentJsonCodec.write(request.getIcon());
    }

    public String serializeCover(CreateDocumentRequest request) {
        return documentJsonCodec.write(request.getCover());
    }

    public DocumentResponse toResponse(Document document) {
        Long version = document.getVersion() == null ? null : document.getVersion().longValue();
        return DocumentResponse.builder()
                .id(document.getId())
                .workspaceId(document.getWorkspaceId())
                .parentId(document.getParentId())
                .title(document.getTitle())
                .icon(documentJsonCodec.read(document.getIconJson()))
                .cover(documentJsonCodec.read(document.getCoverJson()))
                .sortKey(document.getSortKey())
                .createdBy(document.getCreatedBy())
                .updatedBy(document.getUpdatedBy())
                .deletedAt(document.getDeletedAt())
                .version(version)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
