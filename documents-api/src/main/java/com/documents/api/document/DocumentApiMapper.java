package com.documents.api.document;

import com.documents.api.document.dto.CreateDocumentRequest;
import com.documents.api.document.dto.UpdateDocumentRequest;
import com.documents.api.document.dto.DocumentResponse;
import com.documents.api.document.dto.TrashDocumentResponse;
import com.documents.api.document.support.DocumentJsonCodec;
import com.documents.domain.Document;
import com.documents.domain.DocumentTrashPolicy;
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

    public String serializeIcon(UpdateDocumentRequest request) {
        return documentJsonCodec.write(request.getIcon());
    }

    public String serializeCover(UpdateDocumentRequest request) {
        return documentJsonCodec.write(request.getCover());
    }

    public DocumentResponse toResponse(Document document) {
        Long version = document.getVersion() == null ? null : document.getVersion().longValue();
        return DocumentResponse.builder()
                .id(document.getId())
                .parentId(document.getParentId())
                .title(document.getTitle())
                .icon(documentJsonCodec.read(document.getIconJson()))
                .cover(documentJsonCodec.read(document.getCoverJson()))
                .visibility(document.getVisibility())
                .sortKey(document.getSortKey())
                .createdBy(document.getCreatedBy())
                .updatedBy(document.getUpdatedBy())
                .deletedAt(document.getDeletedAt())
                .version(version)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    public TrashDocumentResponse toTrashResponse(Document document) {
        return TrashDocumentResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .parentId(document.getParentId())
                .deletedAt(document.getDeletedAt())
                .purgeAt(document.getDeletedAt().plusMinutes(DocumentTrashPolicy.RETENTION_MINUTES))
                .build();
    }
}
