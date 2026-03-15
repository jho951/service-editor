package com.documents.api.document;

import com.documents.api.document.dto.CreateDocumentRequest;
import com.documents.api.document.dto.DocumentResponse;
import com.documents.domain.Document;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentApiMapper {

    private final ObjectMapper objectMapper;

    public String serializeIcon(CreateDocumentRequest request) {
        return writeJson(request.getIcon());
    }

    public String serializeCover(CreateDocumentRequest request) {
        return writeJson(request.getCover());
    }

    public DocumentResponse toResponse(Document document) {
        Long version = document.getVersion() == null ? null : document.getVersion().longValue();
        return DocumentResponse.builder()
                .id(document.getId())
                .workspaceId(document.getWorkspaceId())
                .parentId(document.getParentId())
                .title(document.getTitle())
                .icon(readJson(document.getIconJson()))
                .cover(readJson(document.getCoverJson()))
                .sortKey(document.getSortKey())
                .createdBy(document.getCreatedBy())
                .updatedBy(document.getUpdatedBy())
                .deletedAt(document.getDeletedAt())
                .version(version)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private String writeJson(JsonNode value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize document JSON field.", ex);
        }
    }

    private JsonNode readJson(String value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize document JSON field.", ex);
        }
    }
}
