package com.documents.service;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionCommand;
import com.documents.service.transaction.DocumentTransactionOperationCommand;
import com.documents.service.transaction.DocumentTransactionOperationStatus;
import com.documents.service.transaction.DocumentTransactionOperationType;
import com.documents.service.transaction.DocumentTransactionResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentTransactionServiceImpl implements DocumentTransactionService {

    private static final String EMPTY_TEXT_BLOCK_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}";

    private final BlockService blockService;
    private final BlockRepository blockRepository;

    @Override
    @Transactional
    public DocumentTransactionResult apply(UUID documentId, DocumentTransactionCommand command, String actorId) {
        Map<String, TempBlockContext> tempBlockContexts = new HashMap<>();
        List<DocumentTransactionAppliedOperationResult> appliedOperations = new ArrayList<>();

        for (DocumentTransactionOperationCommand operation : command.operations()) {
            switch (operation.type()) {
                case BLOCK_CREATE -> appliedOperations.add(
                        applyCreate(documentId, operation, actorId, tempBlockContexts)
                );
                case BLOCK_REPLACE_CONTENT -> appliedOperations.add(
                        applyReplaceContent(operation, actorId, tempBlockContexts)
                );
                case BLOCK_DELETE -> appliedOperations.add(
                        applyDelete(documentId, operation, actorId)
                );
                default -> throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }
        }

        return new DocumentTransactionResult(documentId, command.batchId(), appliedOperations);
    }

    private DocumentTransactionAppliedOperationResult applyCreate(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            String actorId,
            Map<String, TempBlockContext> tempBlockContexts
    ) {
        validateBlockReferenceIsUnique(operation.blockReference(), tempBlockContexts);
        ResolvedCreatePosition resolvedCreatePosition = resolveCreatePosition(operation, tempBlockContexts);

        Block createdBlock = blockService.create(
                documentId,
                resolvedCreatePosition.parentId(),
                BlockType.TEXT,
                EMPTY_TEXT_BLOCK_CONTENT,
                resolvedCreatePosition.afterBlockId(),
                resolvedCreatePosition.beforeBlockId(),
                actorId
        );
        blockRepository.flush();

        registerTempBlockContext(operation.blockReference(), createdBlock, tempBlockContexts);

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                DocumentTransactionOperationStatus.APPLIED,
                operation.blockReference(),
                createdBlock.getId(),
                createdBlock.getVersion(),
                createdBlock.getSortKey(),
                null
        );
    }

    private ResolvedCreatePosition resolveCreatePosition(
            DocumentTransactionOperationCommand operation,
            Map<String, TempBlockContext> tempBlockContexts
    ) {
        return new ResolvedCreatePosition(
                resolveOptionalBlockReference(operation.parentReference(), tempBlockContexts),
                resolveOptionalBlockReference(operation.afterReference(), tempBlockContexts),
                resolveOptionalBlockReference(operation.beforeReference(), tempBlockContexts)
        );
    }

    private DocumentTransactionAppliedOperationResult applyReplaceContent(
            DocumentTransactionOperationCommand operation,
            String actorId,
            Map<String, TempBlockContext> tempBlockContexts
    ) {
        ResolvedBlockReference resolvedBlockReference = resolveBlockReference(operation, tempBlockContexts);
        Block updatedBlock = blockService.update(
                resolvedBlockReference.realBlockId(),
                operation.content(),
                resolvedBlockReference.version(),
                actorId
        );
        blockRepository.flush();

        if (isTempBlockReference(resolvedBlockReference)) {
            registerTempBlockContext(resolvedBlockReference.tempId(), updatedBlock, tempBlockContexts);
        }

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                DocumentTransactionOperationStatus.APPLIED,
                null,
                updatedBlock.getId(),
                updatedBlock.getVersion(),
                updatedBlock.getSortKey(),
                null
        );
    }

    private DocumentTransactionAppliedOperationResult applyDelete(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            String actorId
    ) {
        Block block = resolveExistingBlock(documentId, operation);
        Block deletedBlock = blockService.delete(block.getId(), actorId);

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                DocumentTransactionOperationStatus.APPLIED,
                null,
                deletedBlock.getId(),
                null,
                null,
                deletedBlock.getDeletedAt()
        );
    }

    private ResolvedBlockReference resolveBlockReference(
            DocumentTransactionOperationCommand operation,
            Map<String, TempBlockContext> tempBlockContexts
    ) {
        TempBlockContext tempBlockContext = findTempBlockContext(operation.blockReference(), tempBlockContexts);
        if (isTempBlockReference(tempBlockContext)) {
            return new ResolvedBlockReference(
                    tempBlockContext.realBlockId(),
                    tempBlockContext.version(),
                    operation.blockReference()
            );
        }

        UUID realBlockId = parseRealBlockId(operation.blockReference());
        validateVersionIsPresent(operation.version());

        return new ResolvedBlockReference(realBlockId, operation.version(), null);
    }

    private void validateBlockReferenceIsUnique(String blockReference, Map<String, TempBlockContext> tempBlockContexts) {
        if (tempBlockContexts.containsKey(blockReference)) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private UUID resolveOptionalBlockReference(String blockReference, Map<String, TempBlockContext> tempBlockContexts) {
        if (blockReference == null || blockReference.isBlank()) {
            return null;
        }

        TempBlockContext tempBlockContext = findTempBlockContext(blockReference, tempBlockContexts);
        if (isTempBlockReference(tempBlockContext)) {
            return tempBlockContext.realBlockId();
        }

        return parseRealBlockId(blockReference);
    }

    private void registerTempBlockContext(
            String blockReference,
            Block block,
            Map<String, TempBlockContext> tempBlockContexts
    ) {
        tempBlockContexts.put(blockReference, new TempBlockContext(block.getId(), block.getVersion()));
    }

    private TempBlockContext findTempBlockContext(
            String blockReference,
            Map<String, TempBlockContext> tempBlockContexts
    ) {
        return tempBlockContexts.get(blockReference);
    }

    private UUID parseRealBlockId(String blockReference) {
        try {
            return UUID.fromString(blockReference);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private void validateVersionIsPresent(Integer version) {
        if (version == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private Block resolveExistingBlock(UUID documentId, DocumentTransactionOperationCommand operation) {
        UUID blockId = parseRealBlockId(operation.blockReference());
        validateVersionIsPresent(operation.version());

        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        validateBlockBelongsToDocument(documentId, block);
        validateBlockVersion(operation.version(), block);
        return block;
    }

    private void validateBlockBelongsToDocument(UUID documentId, Block block) {
        if (!documentId.equals(block.getDocumentId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private void validateBlockVersion(Integer version, Block block) {
        if (!block.getVersion().equals(version)) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }
    }

    private boolean isTempBlockReference(ResolvedBlockReference resolvedBlockReference) {
        return resolvedBlockReference.tempId() != null;
    }

    private boolean isTempBlockReference(TempBlockContext tempBlockContext) {
        return tempBlockContext != null;
    }

    private record TempBlockContext(UUID realBlockId, Integer version) {
    }

    private record ResolvedBlockReference(UUID realBlockId, Integer version, String tempId) {
    }

    private record ResolvedCreatePosition(UUID parentId, UUID afterBlockId, UUID beforeBlockId) {
    }
}
