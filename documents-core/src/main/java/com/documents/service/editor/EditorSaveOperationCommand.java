package com.documents.service.editor;

public record EditorSaveOperationCommand(
        String opId,
        EditorSaveOperationType type,
        String blockReference,
        Long version,
        String content,
        String parentReference,
        String afterReference,
        String beforeReference
) {
}
