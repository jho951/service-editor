package com.documents.service.editor;

import java.time.LocalDateTime;
import java.util.UUID;

public record EditorSaveAppliedOperationResult(
        String opId,
        EditorSaveOperationStatus status,
        String tempId,
        UUID blockId,
        Long version,
        String sortKey,
        LocalDateTime deletedAt
) {
}
