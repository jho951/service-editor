package com.documents.api.editor.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EditorSaveAppliedOperationResponse {

    private String opId;
    private String status;
    private String tempId;
    private UUID blockId;
    private Long version;
    private String sortKey;
    private LocalDateTime deletedAt;
}
