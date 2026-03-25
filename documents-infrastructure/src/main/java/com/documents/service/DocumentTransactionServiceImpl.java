package com.documents.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionCommand;
import com.documents.service.transaction.DocumentTransactionOperationCommand;
import com.documents.service.transaction.DocumentTransactionOperationStatus;
import com.documents.service.transaction.DocumentTransactionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentTransactionServiceImpl implements DocumentTransactionService {

    private static final String EMPTY_TEXT_BLOCK_CONTENT =
            "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}";

    private final BlockService blockService;
    private final BlockRepository blockRepository;
    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public DocumentTransactionResult apply(UUID documentId, DocumentTransactionCommand command, String actorId) {
        Document document = findActiveDocument(documentId);

        Map<String, BlockReferenceContext> blockReferenceContexts = new HashMap<>();
        List<DocumentTransactionAppliedOperationResult> appliedOperations = new ArrayList<>();

        for (DocumentTransactionOperationCommand operation : command.operations()) {
            switch (operation.type()) {
                case BLOCK_CREATE -> appliedOperations.add(
                        DocumentVersionIncrementContext.runWithoutIncrement(
                                () -> applyCreate(document, operation, actorId, blockReferenceContexts)
                        )
                );
                case BLOCK_REPLACE_CONTENT -> appliedOperations.add(
                        DocumentVersionIncrementContext.runWithoutIncrement(
                                () -> applyReplaceContent(documentId, operation, actorId, blockReferenceContexts)
                        )
                );
                case BLOCK_MOVE -> appliedOperations.add(
                        DocumentVersionIncrementContext.runWithoutIncrement(
                                () -> applyMove(documentId, operation, actorId, blockReferenceContexts)
                        )
                );
                case BLOCK_DELETE -> appliedOperations.add(
                        DocumentVersionIncrementContext.runWithoutIncrement(
                                () -> applyDelete(documentId, operation, actorId, blockReferenceContexts)
                        )
                );
                default -> throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }
        }

        Integer documentVersion = document.getVersion();
        if (hasEditorChange(appliedOperations)) {
            documentVersion = incrementDocumentVersion(documentId, actorId);
        }

        return new DocumentTransactionResult(documentId, documentVersion, command.batchId(), appliedOperations);
    }

    private DocumentTransactionAppliedOperationResult applyCreate(
            Document document,
            DocumentTransactionOperationCommand operation,
            String actorId,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        validateBlockReferenceIsUnique(operation.blockReference(), blockReferenceContexts);
        ResolvedPositionReferences resolvedCreatePosition = resolvePositionReferences(operation, blockReferenceContexts);

        Block createdBlock = blockService.create(
                document,
                resolvedCreatePosition.parentId(),
                BlockType.TEXT,
                EMPTY_TEXT_BLOCK_CONTENT,
                resolvedCreatePosition.afterBlockId(),
                resolvedCreatePosition.beforeBlockId(),
                actorId
        );
        blockRepository.flush();

        registerBlockReferenceContext(operation.blockReference(), createdBlock, null, true, blockReferenceContexts);

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

    private ResolvedPositionReferences resolvePositionReferences(
            DocumentTransactionOperationCommand operation,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        return new ResolvedPositionReferences(
                resolveOptionalBlockReference(operation.parentReference(), blockReferenceContexts),
                resolveOptionalBlockReference(operation.afterReference(), blockReferenceContexts),
                resolveOptionalBlockReference(operation.beforeReference(), blockReferenceContexts)
        );
    }

    private DocumentTransactionAppliedOperationResult applyReplaceContent(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            String actorId,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        ResolvedBlockReference resolvedBlockReference = resolveBlockReference(documentId, operation, blockReferenceContexts);
        Block updatedBlock = blockService.update(
                resolvedBlockReference.realBlockId(),
                operation.content(),
                resolvedBlockReference.version(),
                actorId
        );
        blockRepository.flush();

        DocumentTransactionOperationStatus status = resolveAppliedStatus(resolvedBlockReference.version(), updatedBlock);

        registerBlockReferenceContext(
                operation.blockReference(),
                updatedBlock,
                resolvedBlockReference.clientVersion(),
                resolvedBlockReference.temporary(),
                blockReferenceContexts
        );

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                status,
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
            String actorId,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        Block block = resolveExistingBlock(documentId, operation, blockReferenceContexts);
        Block deletedBlock = blockService.delete(block.getId(), block.getVersion(), actorId);

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

    private DocumentTransactionAppliedOperationResult applyMove(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            String actorId,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        ResolvedBlockReference resolvedBlockReference = resolveBlockReference(documentId, operation, blockReferenceContexts);
        ResolvedPositionReferences resolvedPositionReferences = resolvePositionReferences(operation, blockReferenceContexts);

        Block movedBlock = blockService.move(
                resolvedBlockReference.realBlockId(),
                resolvedPositionReferences.parentId(),
                resolvedPositionReferences.afterBlockId(),
                resolvedPositionReferences.beforeBlockId(),
                resolvedBlockReference.version(),
                actorId
        );
        blockRepository.flush();

        DocumentTransactionOperationStatus status = resolveAppliedStatus(resolvedBlockReference.version(), movedBlock);

        registerBlockReferenceContext(
                operation.blockReference(),
                movedBlock,
                resolvedBlockReference.clientVersion(),
                resolvedBlockReference.temporary(),
                blockReferenceContexts
        );

        return new DocumentTransactionAppliedOperationResult(
                operation.opId(),
                status,
                null,
                movedBlock.getId(),
                movedBlock.getVersion(),
                movedBlock.getSortKey(),
                null
        );
    }

    private ResolvedBlockReference resolveBlockReference(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        BlockReferenceContext blockReferenceContext = findBlockReferenceContext(operation.blockReference(), blockReferenceContexts);
        if (blockReferenceContext != null) {
            validateRepeatedReferenceVersion(operation.version(), blockReferenceContext);
            return new ResolvedBlockReference(
                    blockReferenceContext.realBlockId(),
                    blockReferenceContext.currentVersion(),
                    blockReferenceContext.clientVersion(),
                    blockReferenceContext.temporary()
            );
        }

        Block block = resolveFirstExistingBlock(documentId, operation);
        return new ResolvedBlockReference(block.getId(), block.getVersion(), operation.version(), false);
    }

    private void validateBlockReferenceIsUnique(String blockReference, Map<String, BlockReferenceContext> blockReferenceContexts) {
        if (blockReferenceContexts.containsKey(blockReference)) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private UUID resolveOptionalBlockReference(String blockReference, Map<String, BlockReferenceContext> blockReferenceContexts) {
        if (blockReference == null || blockReference.isBlank()) {
            return null;
        }

        BlockReferenceContext blockReferenceContext = findBlockReferenceContext(blockReference, blockReferenceContexts);
        if (blockReferenceContext != null) {
            return blockReferenceContext.realBlockId();
        }

        return parseRealBlockId(blockReference);
    }

    private void registerBlockReferenceContext(
            String blockReference,
            Block block,
            Integer clientVersion,
            boolean temporary,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        blockReferenceContexts.put(
                blockReference,
                new BlockReferenceContext(block.getId(), block.getVersion(), clientVersion, temporary)
        );
    }

    private BlockReferenceContext findBlockReferenceContext(
            String blockReference,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        return blockReferenceContexts.get(blockReference);
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

    private Block resolveExistingBlock(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            Map<String, BlockReferenceContext> blockReferenceContexts
    ) {
        BlockReferenceContext blockReferenceContext = findBlockReferenceContext(operation.blockReference(), blockReferenceContexts);
        if (blockReferenceContext != null) {
            return resolveExistingBlockFromContext(documentId, operation, blockReferenceContext);
        }

        return resolveFirstExistingBlock(documentId, operation);
    }

    private Block resolveExistingBlockFromContext(
            UUID documentId,
            DocumentTransactionOperationCommand operation,
            BlockReferenceContext blockReferenceContext
    ) {
        validateRepeatedReferenceVersion(operation.version(), blockReferenceContext);

        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockReferenceContext.realBlockId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));
        validateBlockBelongsToDocument(documentId, block);
        validateBlockVersion(blockReferenceContext.currentVersion(), block);
        return block;
    }

    private Block resolveFirstExistingBlock(UUID documentId, DocumentTransactionOperationCommand operation) {
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

    private void validateRepeatedReferenceVersion(Integer version, BlockReferenceContext blockReferenceContext) {
        if (blockReferenceContext.temporary()) {
            if (version != null) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }
            return;
        }

        validateVersionIsPresent(version);
        if (!blockReferenceContext.clientVersion().equals(version)) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }
    }

    private DocumentTransactionOperationStatus resolveAppliedStatus(Integer requestedVersion, Block block) {
        return block.getVersion().equals(requestedVersion)
                ? DocumentTransactionOperationStatus.NO_OP
                : DocumentTransactionOperationStatus.APPLIED;
    }

    private Document findActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }

    private boolean hasEditorChange(List<DocumentTransactionAppliedOperationResult> appliedOperations) {
        return appliedOperations.stream()
                .anyMatch(result -> result.status() == DocumentTransactionOperationStatus.APPLIED);
    }

    private Integer incrementDocumentVersion(UUID documentId, String actorId) {
        int updatedRowCount = documentRepository.incrementVersion(documentId, actorId, LocalDateTime.now());
        if (updatedRowCount != 1) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        return findActiveDocument(documentId).getVersion();
    }

    private record BlockReferenceContext(
            UUID realBlockId,
            Integer currentVersion,
            Integer clientVersion,
            boolean temporary
    ) {
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
