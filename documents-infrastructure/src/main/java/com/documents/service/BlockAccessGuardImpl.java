package com.documents.service;

import java.util.UUID;

import com.documents.domain.Block;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.BlockRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BlockAccessGuardImpl implements BlockAccessGuard {

    private final BlockRepository blockRepository;
    private final DocumentAccessGuard documentAccessGuard;

    public BlockAccessGuardImpl(
        BlockRepository blockRepository,
        DocumentAccessGuard documentAccessGuard
    ) {
        this.blockRepository = blockRepository;
        this.documentAccessGuard = documentAccessGuard;
    }

    @Transactional(readOnly = true)
    public Block requireReadable(UUID blockId, String actorId) {
        Block block = findActiveBlock(blockId);
        documentAccessGuard.requireReadable(block.getDocumentId(), actorId);
        return block;
    }

    @Transactional(readOnly = true)
    public Block requireWritable(UUID blockId, String actorId) {
        Block block = findActiveBlock(blockId);
        documentAccessGuard.requireWritable(block.getDocumentId(), actorId);
        return block;
    }

    private Block findActiveBlock(UUID blockId) {
        return blockRepository.findByIdAndDeletedAtIsNull(blockId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));
    }
}
