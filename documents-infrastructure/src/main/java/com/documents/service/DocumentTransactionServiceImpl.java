package com.documents.service;

import static com.documents.service.transaction.DocumentTransactionOperationStatus.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionCommand;
import com.documents.service.transaction.DocumentTransactionContext;
import com.documents.service.transaction.DocumentTransactionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentTransactionServiceImpl implements DocumentTransactionService {

    private final DocumentTransactionOperationExecutor operationExecutor;
    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public DocumentTransactionResult apply(UUID documentId, DocumentTransactionCommand command, String actorId) {
        Document document = findActiveDocument(documentId);
        DocumentTransactionContext context = new DocumentTransactionContext();
        List<DocumentTransactionAppliedOperationResult> appliedOperations = command.operations().stream()
                .map(operation -> DocumentVersionIncrementContext.runWithoutIncrement(
                        () -> operationExecutor.apply(documentId, document, operation, actorId, context)
                ))
                .toList();

        Integer documentVersion = document.getVersion();
        if (hasEditorChange(appliedOperations)) {
            documentVersion = incrementDocumentVersion(documentId, actorId);
        }

        return new DocumentTransactionResult(documentId, documentVersion, command.batchId(), appliedOperations);
    }

    private Document findActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }

    private boolean hasEditorChange(List<DocumentTransactionAppliedOperationResult> appliedOperations) {
        return appliedOperations.stream()
                .anyMatch(result -> result.status() == APPLIED);
    }

    private Integer incrementDocumentVersion(UUID documentId, String actorId) {
        int updatedRowCount = documentRepository.incrementVersion(documentId, actorId, LocalDateTime.now());
        if (updatedRowCount != 1) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        return findActiveDocument(documentId).getVersion();
    }
}
