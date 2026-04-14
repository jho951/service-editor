package com.documents.service;

import static com.documents.service.editor.EditorSaveOperationStatus.*;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Block;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveContext;
import com.documents.service.editor.EditorSaveOperationCommand;
import com.documents.service.editor.EditorSaveOperationType;
import com.documents.service.editor.EditorSaveResult;
import com.documents.service.transaction.DocumentVersionUpdater;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminBlockOperationServiceImpl implements AdminBlockOperationService {

    private final BlockService blockService;
    private final DocumentService documentService;
    private final EditorSaveOperationExecutor operationExecutor;
    private final DocumentVersionUpdater documentVersionUpdater;

    @Override
    @Transactional
    public EditorSaveResult applyCreate(
            UUID documentId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    ) {
        Document document = documentService.getById(documentId);
        validateOperationType(operation, EditorSaveOperationType.BLOCK_CREATE);
        return applySingleOperation(documentId, document, batchId, operation, actorId);
    }

    @Override
    @Transactional
    public EditorSaveResult applyReplaceContent(
            UUID blockId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    ) {
        Block block = blockService.getById(blockId);
        validateOperationType(operation, EditorSaveOperationType.BLOCK_REPLACE_CONTENT);
        validateBlockReferenceMatches(blockId, operation);
        Document document = documentService.getById(block.getDocumentId());
        return applySingleOperation(document.getId(), document, batchId, operation, actorId);
    }

    @Override
    @Transactional
    public EditorSaveResult applyMove(
            UUID blockId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    ) {
        Block block = blockService.getById(blockId);
        validateOperationType(operation, EditorSaveOperationType.BLOCK_MOVE);
        validateBlockReferenceMatches(blockId, operation);
        Document document = documentService.getById(block.getDocumentId());
        return applySingleOperation(document.getId(), document, batchId, operation, actorId);
    }

    @Override
    @Transactional
    public EditorSaveResult applyDelete(
            UUID blockId,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    ) {
        Block block = blockService.getById(blockId);
        validateOperationType(operation, EditorSaveOperationType.BLOCK_DELETE);
        validateBlockReferenceMatches(blockId, operation);
        Document document = documentService.getById(block.getDocumentId());
        return applySingleOperation(document.getId(), document, batchId, operation, actorId);
    }

    private EditorSaveResult applySingleOperation(
            UUID documentId,
            Document document,
            String batchId,
            EditorSaveOperationCommand operation,
            String actorId
    ) {
        EditorSaveAppliedOperationResult appliedOperation = DocumentVersionIncrementContext.runWithoutIncrement(
                () -> operationExecutor.apply(documentId, document, operation, actorId, new EditorSaveContext())
        );

        Long documentVersion = document.getVersion().longValue();
        if (appliedOperation.status() == APPLIED) {
            documentVersion = documentVersionUpdater.increment(documentId, actorId, java.time.LocalDateTime.now())
                    .getVersion()
                    .longValue();
        }

        return new EditorSaveResult(documentId, documentVersion, batchId, List.of(appliedOperation));
    }

    private void validateOperationType(
            EditorSaveOperationCommand operation,
            EditorSaveOperationType expectedType
    ) {
        if (operation == null || operation.type() != expectedType) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private void validateBlockReferenceMatches(UUID blockId, EditorSaveOperationCommand operation) {
        if (!blockId.toString().equals(operation.blockReference())) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }
}
