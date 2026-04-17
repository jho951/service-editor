package com.documents.api.editor;

import org.springframework.stereotype.Component;

import com.documents.api.editor.dto.EditorMoveOperationRequest;
import com.documents.api.editor.dto.EditorMoveResponse;
import com.documents.service.editor.EditorMoveCommand;
import com.documents.service.editor.EditorMoveResult;

@Component
public class EditorMoveApiMapper {

    public EditorMoveCommand toCommand(EditorMoveOperationRequest request) {
        return new EditorMoveCommand(
                request.getResourceType(),
                request.getResourceId(),
                request.getTargetParentId(),
                request.getAfterId(),
                request.getBeforeId(),
                request.getVersion()
        );
    }

    public EditorMoveResponse toResponse(EditorMoveResult result) {
        return EditorMoveResponse.builder()
                .resourceType(result.resourceType())
                .resourceId(result.resourceId())
                .parentId(result.parentId())
                .version(result.version())
                .documentVersion(result.documentVersion())
                .sortKey(result.sortKey())
                .build();
    }
}
