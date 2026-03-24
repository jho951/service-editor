package com.documents.service.transaction;

import java.util.List;
import java.util.UUID;

public record DocumentTransactionResult(
        UUID documentId,
        Integer documentVersion,
        String batchId,
        List<DocumentTransactionAppliedOperationResult> appliedOperations
) {
    public DocumentTransactionResult(
            UUID documentId,
            String batchId,
            List<DocumentTransactionAppliedOperationResult> appliedOperations
    ) {
        this(documentId, null, batchId, appliedOperations);
    }
}
