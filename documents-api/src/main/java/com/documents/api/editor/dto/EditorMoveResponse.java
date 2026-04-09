package com.documents.api.editor.dto;

import java.util.UUID;

import com.documents.service.editor.EditorMoveResourceType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EditorMoveResponse {

    private EditorMoveResourceType resourceType;
    private UUID resourceId;
    private UUID parentId;
    private Long version;
    private Long documentVersion;
    private String sortKey;
}
