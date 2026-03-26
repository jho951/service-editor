package com.documents.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import com.documents.support.OrderedSortKeyGenerator;
import com.documents.support.OrderedSortKeyGenerator.SortKeyRebalanceRequiredException;
import com.documents.support.TextNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {

    // TODO: 블록 개수 리밋을 정할 것인지 정책 필요
    private static final int MAX_BLOCK_COUNT_PER_DOCUMENT = 1000;
    // TODO: 블록 깊이 리밋을 정할 것인지 정책 필요
    private static final int MAX_BLOCK_DEPTH = 10;

    private final BlockRepository blockRepository;
    private final DocumentRepository documentRepository;
    private final TextNormalizer textNormalizer;
    private final OrderedSortKeyGenerator orderedSortKeyGenerator;

    @Override
    @Transactional(readOnly = true)
    public List<Block> getAllByDocumentId(UUID documentId) {
        findActiveDocument(documentId);
        return blockRepository.findActiveByDocumentIdOrderBySortKey(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Block getById(UUID blockId) {
        return findActiveBlock(blockId);
    }

    @Override
    @Transactional
    public Block create(
            UUID documentId,
            UUID parentId,
            BlockType type,
            String content,
            UUID afterBlockId,
            UUID beforeBlockId,
            String actorId
    ) {
        Document document = findActiveDocument(documentId);
        return create(document, parentId, type, content, afterBlockId, beforeBlockId, actorId);
    }

    @Override
    @Transactional
    public Block create(
            Document document,
            UUID parentId,
            BlockType type,
            String content,
            UUID afterBlockId,
            UUID beforeBlockId,
            String actorId
    ) {
        UUID documentId = document.getId();
        validateSupportedType(type);
        validateBlockLimit(documentId);

        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        Block parentBlock = validateParent(documentId, parentId);
        validateDepth(parentBlock);

        List<Block> siblings = blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(documentId, parentId);
        String sortKey = generateSortKey(siblings, afterBlockId, beforeBlockId);

        Block newBlock = Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .parent(parentBlock)
                .type(type)
                .content(content)
                .sortKey(sortKey)
                .createdBy(normalizedActorId)
                .updatedBy(normalizedActorId)
                .build();

        Block createdBlock = blockRepository.save(newBlock);
        if (DocumentVersionIncrementContext.shouldIncrement()) {
            incrementActiveDocumentVersion(documentId, normalizedActorId);
        }
        return createdBlock;
    }

    @Override
    @Transactional
    public Block update(UUID blockId, String content, Integer version, String actorId) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        if (!block.getVersion().equals(version)) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }
        if (Objects.equals(block.getContent(), content)) {
            return block;
        }

        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        block.setContent(content);
        block.setUpdatedBy(normalizedActorId);
        if (DocumentVersionIncrementContext.shouldIncrement()) {
            incrementActiveDocumentVersion(block.getDocumentId(), normalizedActorId);
        }
        return block;
    }

    @Override
    @Transactional
    public Block move(UUID blockId, UUID parentId, UUID afterBlockId, UUID beforeBlockId, Integer version, String actorId) {
        Block block = findActiveBlock(blockId);

        if (!block.getVersion().equals(version)) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        Block targetParentBlock = findValidParentForMove(block, parentId);
        validateDepth(targetParentBlock);
        List<Block> siblings = blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(block.getDocumentId(), parentId);
        List<Block> targetSiblings = siblings.stream()
                .filter(sibling -> !blockId.equals(sibling.getId()))
                .toList();

        String nextSortKey = generateSortKey(targetSiblings, afterBlockId, beforeBlockId);

        if (Objects.equals(block.getParentId(), parentId)
                && Objects.equals(block.getSortKey(), nextSortKey)) {
            return block;
        }

        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        block.setParent(targetParentBlock);
        block.setSortKey(nextSortKey);
        block.setUpdatedBy(normalizedActorId);
        if (DocumentVersionIncrementContext.shouldIncrement()) {
            incrementActiveDocumentVersion(block.getDocumentId(), normalizedActorId);
        }
        return block;
    }

    @Override
    @Transactional
    public Block delete(UUID blockId, Integer version, String actorId) {
        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        LocalDateTime deletedAt = LocalDateTime.now();
        Block rootBlock = findActiveBlock(blockId);
        if (!rootBlock.getVersion().equals(version)) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        List<UUID> blockIdsToDelete = collectActiveDescendantBlockIdsForSoftDelete(rootBlock);

        int deletedCount = blockRepository.softDeleteActiveByIdsWithRootVersion(
                blockIdsToDelete,
                rootBlock.getId(),
                version,
                normalizedActorId,
                deletedAt
        );
        if (deletedCount == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        rootBlock.setDeletedAt(deletedAt);
        rootBlock.setUpdatedAt(deletedAt);
        rootBlock.setUpdatedBy(normalizedActorId);
        rootBlock.setVersion(version + 1);
        if (DocumentVersionIncrementContext.shouldIncrement()) {
            incrementActiveDocumentVersion(rootBlock.getDocumentId(), normalizedActorId);
        }
        return rootBlock;
    }

    @Override
    @Transactional
    public void softDeleteAllByDocumentId(UUID documentId, String actorId, LocalDateTime deletedAt) {
        if (blockRepository.countActiveByDocumentId(documentId) == 0) {
            return;
        }

        blockRepository.softDeleteActiveByDocumentId(documentId, actorId, deletedAt);
        if (DocumentVersionIncrementContext.shouldIncrement()) {
            incrementActiveDocumentVersion(documentId, actorId, deletedAt);
        }
    }

    @Override
    @Transactional
    public void restoreAllByDocumentId(UUID documentId, String actorId, LocalDateTime updatedAt) {
        if (blockRepository.countDeletedByDocumentId(documentId) == 0) {
            return;
        }

        blockRepository.restoreDeletedByDocumentId(documentId, actorId, updatedAt);
        if (DocumentVersionIncrementContext.shouldIncrement()) {
            incrementActiveDocumentVersion(documentId, actorId, updatedAt);
        }
    }

    private Document findActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
    }

    private Block findActiveBlock(UUID blockId) {
        return blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));
    }

    private void validateSupportedType(BlockType type) {
        if (type != BlockType.TEXT) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private void validateBlockLimit(UUID documentId) {
        if (blockRepository.countActiveByDocumentId(documentId) >= MAX_BLOCK_COUNT_PER_DOCUMENT) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private Block validateParent(UUID documentId, UUID parentId) {
        if (parentId == null) {
            return null;
        }

        Block parentBlock = blockRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        if (!documentId.equals(parentBlock.getDocumentId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        return parentBlock;
    }

    private Block findValidParentForMove(Block block, UUID parentId) {
        if (Objects.equals(block.getId(), parentId)) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        if (parentId == null) {
            return null;
        }

        Block parentBlock = findActiveBlock(parentId);
        if (!block.getDocumentId().equals(parentBlock.getDocumentId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }

        validateNoCycle(block.getId(), parentBlock);
        return parentBlock;
    }

    private void validateDepth(Block parentBlock) {
        int depth = 1;
        Block current = parentBlock;

        while (current != null) {
            depth++;
            if (depth > MAX_BLOCK_DEPTH) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }

            current = current.getParentId() == null ? null : findActiveBlock(current.getParentId());
        }
    }

    private void validateNoCycle(UUID blockId, Block parentBlock) {
        Block current = parentBlock;

        while (current != null) {
            if (blockId.equals(current.getId())) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }

            current = current.getParentId() == null ? null : findActiveBlock(current.getParentId());
        }
    }

    private String generateSortKey(List<Block> siblings, UUID afterBlockId, UUID beforeBlockId) {
        try {
            return orderedSortKeyGenerator.generate(
                    siblings,
                    Block::getId,
                    Block::getSortKey,
                    afterBlockId,
                    beforeBlockId
            );
        } catch (SortKeyRebalanceRequiredException ex) {
            throw new BusinessException(BusinessErrorCode.SORT_KEY_REBALANCE_REQUIRED);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
    }

    private List<UUID> collectActiveDescendantBlockIdsForSoftDelete(Block rootBlock) {
        List<UUID> blockIds = new ArrayList<>();
        blockIds.add(rootBlock.getId());

        List<Block> children = blockRepository.findActiveChildrenByParentIdOrderBySortKey(rootBlock.getId());
        for (Block child : children) {
            blockIds.addAll(collectActiveDescendantBlockIdsForSoftDelete(child));
        }

        return blockIds;
    }

    private void incrementActiveDocumentVersion(UUID documentId, String actorId, LocalDateTime updatedAt) {
        int updatedRowCount = documentRepository.incrementVersion(documentId, actorId, updatedAt);
        if (updatedRowCount != 1) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }
    }

    private void incrementActiveDocumentVersion(UUID documentId, String actorId) {
        incrementActiveDocumentVersion(documentId, actorId, LocalDateTime.now());
    }

}
