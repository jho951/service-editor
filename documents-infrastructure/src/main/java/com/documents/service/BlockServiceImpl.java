package com.documents.service;

import java.time.LocalDateTime;
import java.util.List;
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

        return blockRepository.save(newBlock);
    }

    @Override
    @Transactional
    public Block update(UUID blockId, String content, Integer version, String actorId) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
        if (!block.getVersion().equals(version)) {
            throw new BusinessException(BusinessErrorCode.CONFLICT);
        }

        block.setContent(content);
        block.setUpdatedBy(normalizedActorId);
        return block;
    }

    @Override
    @Transactional
    public void softDeleteAllByDocumentId(UUID documentId, String actorId, LocalDateTime deletedAt) {
        blockRepository.softDeleteActiveByDocumentId(documentId, actorId, deletedAt);
    }

    @Override
    @Transactional
    public void restoreAllByDocumentId(UUID documentId, String actorId, LocalDateTime updatedAt) {
        blockRepository.restoreDeletedByDocumentId(documentId, actorId, updatedAt);
    }

    private Document findActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
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

    private void validateDepth(Block parentBlock) {
        int depth = 1;
        Block current = parentBlock;
        while (current != null) {
            depth++;
            if (depth > MAX_BLOCK_DEPTH) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
            }
            current = current.getParentId() == null ? null : blockRepository.findByIdAndDeletedAtIsNull(current.getParentId())
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));
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
}
