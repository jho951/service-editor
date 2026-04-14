package com.documents.service.editor;

import java.util.List;

public record EditorSaveCommand(
        String clientId,
        String batchId,
        List<EditorSaveOperationCommand> operations
) {
}
