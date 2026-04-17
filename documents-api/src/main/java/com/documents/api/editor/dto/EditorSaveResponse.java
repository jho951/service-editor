package com.documents.api.editor.dto;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EditorSaveResponse {

    private UUID documentId;
    private Long documentVersion;
    private String batchId;
    private List<EditorSaveAppliedOperationResponse> appliedOperations;
}
