package com.documents.api.document.dto;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentTransactionResponse {

    private UUID documentId;
    private Long documentVersion;
    private String batchId;
    private List<DocumentTransactionAppliedOperationResponse> appliedOperations;
}
