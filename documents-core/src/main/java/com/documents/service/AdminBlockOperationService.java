package com.documents.service;

import java.util.UUID;

import com.documents.service.editor.EditorSaveOperationCommand;
import com.documents.service.editor.EditorSaveResult;

public interface AdminBlockOperationService {

    EditorSaveResult applyCreate(
            UUID documentId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    );

    EditorSaveResult applyReplaceContent(
            UUID blockId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    );

    EditorSaveResult applyMove(
            UUID blockId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    );

    EditorSaveResult applyDelete(
            UUID blockId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    );
}
