package com.documents.service;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveCommand;
import com.documents.service.editor.EditorSaveContext;
import com.documents.service.editor.EditorSaveOperationStatus;
import com.documents.service.editor.EditorSaveResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditorOperationOrchestratorImpl implements EditorOperationOrchestrator {

    private final EditorSaveOperationExecutor editorSaveOperationExecutor;
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
