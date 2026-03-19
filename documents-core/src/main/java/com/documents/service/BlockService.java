package com.documents.service;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import java.util.List;
import java.util.UUID;

public interface BlockService {
    List<Block> getAllByDocumentId(UUID documentId);

    Block create(
            UUID documentId,
            UUID parentId,
            BlockType type,
            String text,
            UUID afterBlockId,
            UUID beforeBlockId,
            String actorId
    );

    Block update(UUID blockId, String text, Integer version, String actorId);
}
