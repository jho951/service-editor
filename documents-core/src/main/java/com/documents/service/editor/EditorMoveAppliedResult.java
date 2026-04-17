package com.documents.service.editor;

import java.util.UUID;

public record EditorMoveAppliedResult(
        UUID blockId,
        UUID parentId,
        Long version,
        String sortKey
) {
}
