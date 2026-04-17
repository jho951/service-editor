package com.documents.service;

import org.springframework.stereotype.Component;

import com.documents.domain.Document;
import com.documents.service.editor.EditorMoveAppliedResult;
import com.documents.service.editor.EditorMoveResourceType;
import com.documents.service.editor.EditorMoveResult;

@Component
public class EditorMoveResultMapper {

    public EditorMoveResult toDocumentResult(Document document) {
        Long documentVersion = document.getVersion().longValue();

        return new EditorMoveResult(
                EditorMoveResourceType.DOCUMENT,
                document.getId(),
                document.getParentId(),
                documentVersion,
                documentVersion,
                document.getSortKey()
        );
    }

    public EditorMoveResult toBlockResult(EditorMoveAppliedResult appliedResult, Document document) {
        return new EditorMoveResult(
                EditorMoveResourceType.BLOCK,
                appliedResult.blockId(),
                appliedResult.parentId(),
                appliedResult.version(),
                document.getVersion().longValue(),
                appliedResult.sortKey()
        );
    }
}
