package com.documents.service.editor;

import java.util.UUID;

public record EditorMoveResult(
        EditorMoveResourceType resourceType,
        UUID resourceId,
        UUID parentId,
        Long version,
        Long documentVersion,
        String sortKey
) {
}
