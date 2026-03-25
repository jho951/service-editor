package com.documents.service;

import static com.documents.service.transaction.DocumentTransactionOperationStatus.APPLIED;

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
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionContext;
import com.documents.service.transaction.DocumentTransactionOperationCommand;
import com.documents.service.transaction.DocumentTransactionOperationType;
import com.documents.service.transaction.DocumentTransactionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminBlockTransactionServiceImpl implements AdminBlockTransactionService {

    private final BlockService blockService;
    private final DocumentRepository documentRepository;
    private final DocumentTransactionOperationExecutor operationExecutor;

    @Override
    @Transactional
    public DocumentTransactionResult applyCreate(
            UUID documentId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    ) {
        Document document = findActiveDocument(documentId);
        validateOperationType(operation, DocumentTransactionOperationType.BLOCK_CREATE);
        return applySingleOperation(documentId, document, batchId, operation, actorId);
    }

    @Override
    @Transactional
    public DocumentTransactionResult applyReplaceContent(
            UUID blockId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    ) {
        Block block = blockService.getById(blockId);
        validateOperationType(operation, DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT);
        validateBlockReferenceMatches(blockId, operation);
        Document document = findActiveDocument(block.getDocumentId());
        return applySingleOperation(document.getId(), document, batchId, operation, actorId);
    }

    @Override
    @Transactional
    public DocumentTransactionResult applyMove(
            UUID blockId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    ) {
        Block block = blockService.getById(blockId);
        validateOperationType(operation, DocumentTransactionOperationType.BLOCK_MOVE);
        validateBlockReferenceMatches(blockId, operation);
        Document document = findActiveDocument(block.getDocumentId());
        return applySingleOperation(document.getId(), document, batchId, operation, actorId);
    }

    @Override
    @Transactional
    public DocumentTransactionResult applyDelete(
            UUID blockId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    ) {
        Block block = blockService.getById(blockId);
        validateOperationType(operation, DocumentTransactionOperationType.BLOCK_DELETE);
        validateBlockReferenceMatches(blockId, operation);
        Document document = findActiveDocument(block.getDocumentId());
        return applySingleOperation(document.getId(), document, batchId, operation, actorId);
    }

    private DocumentTransactionResult applySingleOperation(
            UUID documentId,
            Document document,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    ) {
        DocumentTransactionAppliedOperationResult appliedOperation = DocumentVersionIncrementContext.runWithoutIncrement(
                () -> operationExecutor.apply(documentId, document, operation, actorId, new DocumentTransactionContext())
        );

        Integer documentVersion = document.getVersion();
        if (appliedOperation.status() == APPLIED) {
            documentVersion = incrementDocumentVersion(documentId, actorId);
        }

        return new DocumentTransactionResult(documentId, documentVersion, batchId, List.of(appliedOperation));
    }

    private void validateOperationType(
            DocumentTransactionOperationCommand operation,
            DocumentTransactionOperationType expectedType
    ) {
        if (operation == null || operation.type() != expectedType) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private void validateBlockReferenceMatches(UUID blockId, DocumentTransactionOperationCommand operation) {
        if (!blockId.toString().equals(operation.blockReference())) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private Document findActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }

    private Integer incrementDocumentVersion(UUID documentId, String actorId) {
        int updatedRowCount = documentRepository.incrementVersion(documentId, actorId, LocalDateTime.now());
        if (updatedRowCount != 1) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        return findActiveDocument(documentId).getVersion();
    }
}
