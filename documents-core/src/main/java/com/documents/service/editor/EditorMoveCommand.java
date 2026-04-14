package com.documents.service.editor;

import java.util.UUID;

public record EditorMoveCommand(
        EditorMoveResourceType resourceType,
        UUID resourceId,
        UUID targetParentId,
        UUID afterId,
        UUID beforeId,
        Long version
) {
}
