package com.documents.service;

import java.util.UUID;

import com.documents.service.editor.EditorSaveCommand;
import com.documents.service.editor.EditorSaveResult;

public interface EditorOperationOrchestrator {

    EditorSaveResult save(UUID documentId, EditorSaveCommand command, String actorId);
}
