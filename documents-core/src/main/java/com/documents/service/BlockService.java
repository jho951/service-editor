package com.documents.service;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import java.util.UUID;

public interface BlockService {
    Block create(
            UUID documentId,
            UUID parentId,
            BlockType type,
            String text,
            UUID afterBlockId,
            UUID beforeBlockId,
            String actorId
    );
}
