package com.documents.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Block;
import com.documents.domain.Document;
import com.documents.service.editor.EditorMoveAppliedResult;
import com.documents.service.editor.EditorMoveCommand;
import com.documents.service.editor.EditorMoveResult;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveCommand;
import com.documents.service.editor.EditorSaveContext;
import com.documents.service.editor.EditorSaveOperationStatus;
import com.documents.service.editor.EditorSaveResult;
import com.documents.service.transaction.DocumentVersionUpdater;
import com.documents.service.transaction.PersistenceContextManager;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditorOperationOrchestratorImpl implements EditorOperationOrchestrator {

    private final DocumentService documentService;
    private final BlockService blockService;
    private final EditorSaveOperationExecutor editorSaveOperationExecutor;
    private final EditorMoveResultMapper editorMoveResultMapper;
    private final DocumentVersionUpdater documentVersionUpdater;
    private final PersistenceContextManager persistenceContextManager;

    @Override
    @Transactional
    public EditorSaveResult save(UUID documentId, EditorSaveCommand command, String actorId) {
        Document document = documentService.getById(documentId);
        EditorSaveContext context = new EditorSaveContext();
        List<EditorSaveAppliedOperationResult> appliedOperations = command.operations().stream()
                .map(operation -> DocumentVersionIncrementContext.runWithoutIncrement(
                        () -> editorSaveOperationExecutor.apply(documentId, document, operation, actorId, context)
                ))
                .toList();

        Long documentVersion = document.getVersion().longValue();
        if (hasEditorChange(appliedOperations)) {
            documentVersion = documentVersionUpdater.increment(documentId, actorId, LocalDateTime.now())
                    .getVersion()
                    .longValue();
        }

        return new EditorSaveResult(documentId, documentVersion, command.batchId(), appliedOperations);
    }

    @Override
    @Transactional
    public EditorMoveResult move(EditorMoveCommand command, String actorId) {
        return switch (command.resourceType()) {
            case DOCUMENT -> moveDocument(command, actorId);
            case BLOCK -> moveBlock(command, actorId);
        };
    }

    private EditorMoveResult moveDocument(EditorMoveCommand command, String actorId) {
        Document movedDocument = documentService.move(
                command.resourceId(),
                command.targetParentId(),
                command.afterId(),
                command.beforeId(),
                actorId
        );
        persistenceContextManager.flush();
        return editorMoveResultMapper.toDocumentResult(movedDocument);
    }

    private EditorMoveResult moveBlock(EditorMoveCommand command, String actorId) {
        Block block = blockService.getById(command.resourceId());
        UUID documentId = block.getDocumentId();

        EditorMoveAppliedResult appliedOperation = editorSaveOperationExecutor.applyMove(
                command.resourceId(),
                command.targetParentId(),
                command.afterId(),
                command.beforeId(),
                command.version(),
                actorId
        );

        return editorMoveResultMapper.toBlockResult(appliedOperation, documentService.getById(documentId));
    }

    private boolean hasEditorChange(List<EditorSaveAppliedOperationResult> appliedOperations) {
        return appliedOperations.stream()
                .anyMatch(result -> result.status() == EditorSaveOperationStatus.APPLIED);
    }
}
