package com.documents.service.editor;

import java.util.List;
import java.util.UUID;

public record EditorSaveResult(
        UUID documentId,
        Long documentVersion,
        String batchId,
        List<EditorSaveAppliedOperationResult> appliedOperations
) {
}
