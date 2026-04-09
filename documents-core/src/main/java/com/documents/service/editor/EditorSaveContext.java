package com.documents.service.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditorSaveContext {

    private final Map<String, BlockReferenceState> blockReferenceStates = new HashMap<>();

    public boolean contains(String blockReference) {
        return blockReferenceStates.containsKey(blockReference);
    }

    public BlockReferenceState get(String blockReference) {
        return blockReferenceStates.get(blockReference);
    }

    public void put(String blockReference, UUID realBlockId, Long currentVersion, Long clientVersion, boolean temporary) {
        blockReferenceStates.put(
                blockReference,
                new BlockReferenceState(realBlockId, currentVersion, clientVersion, temporary)
        );
    }

    public record BlockReferenceState(
            UUID realBlockId,
            Long currentVersion,
            Long clientVersion,
            boolean temporary
    ) {
    }
}
