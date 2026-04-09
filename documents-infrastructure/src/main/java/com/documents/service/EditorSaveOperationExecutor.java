package com.documents.service;

import static com.documents.service.editor.EditorSaveOperationStatus.APPLIED;
import static com.documents.service.editor.EditorSaveOperationStatus.NO_OP;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveContext;
import com.documents.service.editor.EditorSaveOperationCommand;
import com.documents.service.editor.EditorSaveOperationStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EditorSaveOperationExecutor {

    private static final String EMPTY_TEXT_BLOCK_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}";

    private final BlockService blockService;
    private final BlockRepository blockRepository;

    public EditorSaveAppliedOperationResult apply(
            UUID documentId,
            Document document,
            EditorSaveOperationCommand operation,
            String actorId,
            EditorSaveContext context
    ) {
        return switch (operation.type()) {
            case BLOCK_CREATE -> applyCreate(document, operation, actorId, context);
            case BLOCK_REPLACE_CONTENT -> applyReplaceContent(documentId, operation, actorId, context);
            case BLOCK_MOVE -> applyMove(documentId, operation, actorId, context);
            case BLOCK_DELETE -> applyDelete(documentId, operation, actorId, context);
        };
    }

    private EditorSaveAppliedOperationResult applyCreate(
            Document document,
            EditorSaveOperationCommand operation,
            String actorId,
            EditorSaveContext context
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

        return new EditorSaveAppliedOperationResult(
                operation.opId(),
                APPLIED,
                operation.blockReference(),
                createdBlock.getId(),
                createdBlock.getVersion().longValue(),
                createdBlock.getSortKey(),
                null
        );
    }

    private String resolveCreateContent(EditorSaveOperationCommand operation) {
        if (operation.content() != null) {
            return operation.content();
        }
        return EMPTY_TEXT_BLOCK_CONTENT;
    }

    private EditorSaveAppliedOperationResult applyReplaceContent(
            UUID documentId,
            EditorSaveOperationCommand operation,
            String actorId,
            EditorSaveContext context
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

        return new EditorSaveAppliedOperationResult(
                operation.opId(),
                resolveAppliedStatus(resolvedBlockReference.version(), updatedBlock),
                null,
                updatedBlock.getId(),
                updatedBlock.getVersion().longValue(),
                updatedBlock.getSortKey(),
                null
        );
    }

    private EditorSaveAppliedOperationResult applyMove(
            UUID documentId,
            EditorSaveOperationCommand operation,
            String actorId,
            EditorSaveContext context
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

        return new EditorSaveAppliedOperationResult(
                operation.opId(),
                resolveAppliedStatus(resolvedBlockReference.version(), movedBlock),
                null,
                movedBlock.getId(),
                movedBlock.getVersion().longValue(),
                movedBlock.getSortKey(),
                null
        );
    }

    private EditorSaveAppliedOperationResult applyDelete(
            UUID documentId,
            EditorSaveOperationCommand operation,
            String actorId,
            EditorSaveContext context
    ) {
        Block block = resolveExistingBlock(documentId, operation, context);
        Block deletedBlock = blockService.delete(block.getId(), block.getVersion(), actorId);
        blockRepository.flush();

        return new EditorSaveAppliedOperationResult(
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
            EditorSaveOperationCommand operation,
            EditorSaveContext context
    ) {
        return new ResolvedPositionReferences(
                resolveOptionalBlockReference(operation.parentReference(), context),
                resolveOptionalBlockReference(operation.afterReference(), context),
                resolveOptionalBlockReference(operation.beforeReference(), context)
        );
    }

    private ResolvedBlockReference resolveBlockReference(
            UUID documentId,
            EditorSaveOperationCommand operation,
            EditorSaveContext context
    ) {
        EditorSaveContext.BlockReferenceState state = context.get(operation.blockReference());
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

    private void validateBlockReferenceIsUnique(String blockReference, EditorSaveContext context) {
        if (context.contains(blockReference)) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private UUID resolveOptionalBlockReference(String blockReference, EditorSaveContext context) {
        if (blockReference == null || blockReference.isBlank()) {
            return null;
        }

        EditorSaveContext.BlockReferenceState state = context.get(blockReference);
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
            EditorSaveOperationCommand operation,
            EditorSaveContext context
    ) {
        EditorSaveContext.BlockReferenceState state = context.get(operation.blockReference());
        if (state != null) {
            return resolveExistingBlockFromContext(documentId, operation, state);
        }

        return resolveFirstExistingBlock(documentId, operation);
    }

    private Block resolveExistingBlockFromContext(
            UUID documentId,
            EditorSaveOperationCommand operation,
            EditorSaveContext.BlockReferenceState state
    ) {
        validateRepeatedReferenceVersion(operation.version(), state);

        Block block = blockService.getById(state.realBlockId());
        validateBlockBelongsToDocument(documentId, block);
        validateBlockVersion(state.currentVersion(), block);
        return block;
    }

    private Block resolveFirstExistingBlock(UUID documentId, EditorSaveOperationCommand operation) {
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
            EditorSaveContext.BlockReferenceState state
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

    private EditorSaveOperationStatus resolveAppliedStatus(Integer requestedVersion, Block block) {
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
