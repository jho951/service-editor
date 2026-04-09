package com.documents.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Block;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.service.editor.EditorMoveAppliedResult;
import com.documents.service.editor.EditorMoveCommand;
import com.documents.service.editor.EditorMoveResult;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveCommand;
import com.documents.service.editor.EditorSaveContext;
import com.documents.service.editor.EditorSaveOperationStatus;
import com.documents.service.editor.EditorSaveResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditorOperationOrchestratorImpl implements EditorOperationOrchestrator {

    private final DocumentService documentService;
    private final BlockService blockService;
    private final EditorSaveOperationExecutor editorSaveOperationExecutor;
    private final EditorMoveResultMapper editorMoveResultMapper;
    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public EditorSaveResult save(UUID documentId, EditorSaveCommand command, String actorId) {
        Document document = findActiveDocument(documentId);
        EditorSaveContext context = new EditorSaveContext();
        List<EditorSaveAppliedOperationResult> appliedOperations = command.operations().stream()
                .map(operation -> DocumentVersionIncrementContext.runWithoutIncrement(
                        () -> editorSaveOperationExecutor.apply(documentId, document, operation, actorId, context)
                ))
                .toList();

        Long documentVersion = document.getVersion().longValue();
        if (hasEditorChange(appliedOperations)) {
            documentVersion = incrementDocumentVersion(documentId, actorId).longValue();
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
        documentService.move(
                command.resourceId(),
                command.targetParentId(),
                command.afterId(),
                command.beforeId(),
                actorId
        );
        documentRepository.flush();

        Document movedDocument = findActiveDocument(command.resourceId());
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

        Document document = findActiveDocument(documentId);
        return editorMoveResultMapper.toBlockResult(appliedOperation, document);
    }

    private Document findActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }

    private boolean hasEditorChange(List<EditorSaveAppliedOperationResult> appliedOperations) {
        return appliedOperations.stream()
                .anyMatch(result -> result.status() == EditorSaveOperationStatus.APPLIED);
    }

    private Integer incrementDocumentVersion(UUID documentId, String actorId) {
        int updatedRowCount = documentRepository.incrementVersion(documentId, actorId, LocalDateTime.now());
        if (updatedRowCount != 1) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        return findActiveDocument(documentId).getVersion();
    }
}
