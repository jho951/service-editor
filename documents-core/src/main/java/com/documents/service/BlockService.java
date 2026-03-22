package com.documents.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.documents.domain.Block;
import com.documents.domain.BlockType;

public interface BlockService {
    List<Block> getAllByDocumentId(UUID documentId);

    Block create(
            UUID documentId,
            UUID parentId,
            BlockType type,
            String content,
            UUID afterBlockId,
            UUID beforeBlockId,
            String actorId
    );

    Block update(UUID blockId, String content, Integer version, String actorId);

    Block move(UUID blockId, UUID parentId, UUID afterBlockId, UUID beforeBlockId, Integer version, String actorId);

    Block delete(UUID blockId, String actorId);

    void softDeleteAllByDocumentId(UUID documentId, String actorId, LocalDateTime deletedAt);

    void restoreAllByDocumentId(UUID documentId, String actorId, LocalDateTime updatedAt);
}
