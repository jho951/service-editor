package com.documents.service;

import static com.documents.service.transaction.DocumentTransactionOperationStatus.APPLIED;
import static com.documents.service.transaction.DocumentTransactionOperationStatus.NO_OP;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionContext;
import com.documents.service.transaction.DocumentTransactionOperationCommand;
import com.documents.service.transaction.DocumentTransactionOperationStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentTransactionOperationExecutor {

    private static final String EMPTY_TEXT_BLOCK_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}";

    private final BlockService blockService;
    private final BlockRepository blockRepository;

    public DocumentTransactionAppliedOperationResult apply(
            UUID documentId,
            Document document,
            DocumentTransactionOperationCommand operation,
            String actorId,
            DocumentTransactionContext context
    ) {
        return switch (operation.type()) {
            case BLOCK_CREATE -> applyCreate(document, operation, actorId, context);
            case BLOCK_REPLACE_CONTENT -> applyReplaceContent(documentId, operation, actorId, context);
            case BLOCK_MOVE -> applyMove(documentId, operation, actorId, context);
            case BLOCK_DELETE -> applyDelete(documentId, operation, actorId, context);
        };
    }

    private DocumentTransactionAppliedOperationResult applyCreate(
            Document document,
            DocumentTransactionOperationCommand operation,
            String actorId,
            DocumentTransactionContext context
    ) {
        validateBlockReferenceIsUnique(operation.blockReference(), context);
        ResolvedPositionReferences resolvedPositionReferences = resolvePositionReferences(operation, context);
        String initialContent = resolveCreateContent(operation);

        Block createdBlock = blockService.create(
                document,
                resolvedPositionReferences.parentId(),
                BlockType.TEXT,
                initialContent,
                resolvedPositionReferences.afterBlockId(),
                resolvedPositionReferences.beforeBlockId(),
                actorId
        );
        blockRepository.flush();

        context.put(operation.blockReference(), createdBlock.getId(), createdBlock.getVersion(), null, true);

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                APPLIED,
                operation.blockReference(),
                createdBlock.getId(),
                createdBlock.getVersion(),
                createdBlock.getSortKey(),
                null
        );
    }

    private String resolveCreateContent(DocumentTransactionOperationCommand operation) {
        if (operation.content() != null) {
            return operation.content();
        }
        return EMPTY_TEXT_BLOCK_CONTENT;
    }

    private DocumentTransactionAppliedOperationResult applyReplaceContent(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            String actorId,
            DocumentTransactionContext context
    ) {
        ResolvedBlockReference resolvedBlockReference = resolveBlockReference(documentId, operation, context);
        Block updatedBlock = blockService.update(
                resolvedBlockReference.realBlockId(),
                operation.content(),
                resolvedBlockReference.version(),
                actorId
        );
        blockRepository.flush();

        context.put(
                operation.blockReference(),
                updatedBlock.getId(),
                updatedBlock.getVersion(),
                resolvedBlockReference.clientVersion(),
                resolvedBlockReference.temporary()
        );

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                resolveAppliedStatus(resolvedBlockReference.version(), updatedBlock),
                null,
                updatedBlock.getId(),
                updatedBlock.getVersion(),
                updatedBlock.getSortKey(),
                null
        );
    }

    private DocumentTransactionAppliedOperationResult applyMove(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            String actorId,
            DocumentTransactionContext context
    ) {
        ResolvedBlockReference resolvedBlockReference = resolveBlockReference(documentId, operation, context);
        ResolvedPositionReferences resolvedPositionReferences = resolvePositionReferences(operation, context);

        Block movedBlock = blockService.move(
                resolvedBlockReference.realBlockId(),
                resolvedPositionReferences.parentId(),
                resolvedPositionReferences.afterBlockId(),
                resolvedPositionReferences.beforeBlockId(),
                resolvedBlockReference.version(),
                actorId
        );
        blockRepository.flush();

        context.put(
                operation.blockReference(),
                movedBlock.getId(),
                movedBlock.getVersion(),
                resolvedBlockReference.clientVersion(),
                resolvedBlockReference.temporary()
        );

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                resolveAppliedStatus(resolvedBlockReference.version(), movedBlock),
                null,
                movedBlock.getId(),
                movedBlock.getVersion(),
                movedBlock.getSortKey(),
                null
        );
    }

    private DocumentTransactionAppliedOperationResult applyDelete(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            String actorId,
            DocumentTransactionContext context
    ) {
        Block block = resolveExistingBlock(documentId, operation, context);
        Block deletedBlock = blockService.delete(block.getId(), block.getVersion(), actorId);
        blockRepository.flush();

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                APPLIED,
                null,
                deletedBlock.getId(),
                null,
                null,
                deletedBlock.getDeletedAt()
        );
    }

    private ResolvedPositionReferences resolvePositionReferences(
            DocumentTransactionOperationCommand operation,
            DocumentTransactionContext context
    ) {
        return new ResolvedPositionReferences(
                resolveOptionalBlockReference(operation.parentReference(), context),
                resolveOptionalBlockReference(operation.afterReference(), context),
                resolveOptionalBlockReference(operation.beforeReference(), context)
        );
    }

    private ResolvedBlockReference resolveBlockReference(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            DocumentTransactionContext context
    ) {
        DocumentTransactionContext.BlockReferenceState state = context.get(operation.blockReference());
        if (state != null) {
            validateRepeatedReferenceVersion(operation.version(), state);
            return new ResolvedBlockReference(
                    state.realBlockId(),
                    state.currentVersion(),
                    state.clientVersion(),
                    state.temporary()
            );
        }

        Block block = resolveFirstExistingBlock(documentId, operation);
        return new ResolvedBlockReference(block.getId(), block.getVersion(), operation.version(), false);
    }

    private void validateBlockReferenceIsUnique(String blockReference, DocumentTransactionContext context) {
        if (context.contains(blockReference)) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private UUID resolveOptionalBlockReference(String blockReference, DocumentTransactionContext context) {
        if (blockReference == null || blockReference.isBlank()) {
            return null;
        }

        DocumentTransactionContext.BlockReferenceState state = context.get(blockReference);
        if (state != null) {
            return state.realBlockId();
        }

        return parseRealBlockId(blockReference);
    }

    private UUID parseRealBlockId(String blockReference) {
        try {
            return UUID.fromString(blockReference);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private Block resolveExistingBlock(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            DocumentTransactionContext context
    ) {
        DocumentTransactionContext.BlockReferenceState state = context.get(operation.blockReference());
        if (state != null) {
            return resolveExistingBlockFromContext(documentId, operation, state);
        }

        return resolveFirstExistingBlock(documentId, operation);
    }

    private Block resolveExistingBlockFromContext(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            DocumentTransactionContext.BlockReferenceState state
    ) {
        validateRepeatedReferenceVersion(operation.version(), state);

        Block block = blockService.getById(state.realBlockId());
        validateBlockBelongsToDocument(documentId, block);
        validateBlockVersion(state.currentVersion(), block);
        return block;
    }

    private Block resolveFirstExistingBlock(UUID documentId, DocumentTransactionOperationCommand operation) {
        UUID blockId = parseRealBlockId(operation.blockReference());
        validateVersionIsPresent(operation.version());

        Block block = blockService.getById(blockId);
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

    private void validateRepeatedReferenceVersion(
            Integer version,
            DocumentTransactionContext.BlockReferenceState state
    ) {
        if (state.temporary()) {
            if (version != null) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }
            return;
        }

        validateVersionIsPresent(version);
        if (!state.clientVersion().equals(version)) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }
    }

    private void validateVersionIsPresent(Integer version) {
        if (version == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private DocumentTransactionOperationStatus resolveAppliedStatus(Integer requestedVersion, Block block) {
        return block.getVersion().equals(requestedVersion) ? NO_OP : APPLIED;
    }

    private record ResolvedBlockReference(
            UUID realBlockId,
            Integer version,
            Integer clientVersion,
            boolean temporary
    ) {
    }

    private record ResolvedPositionReferences(UUID parentId, UUID afterBlockId, UUID beforeBlockId) {
    }
}
