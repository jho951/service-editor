package com.documents.service.transaction;

import java.util.List;
import java.util.UUID;

public record DocumentTransactionResult(
        UUID documentId,
        String batchId,
        List<DocumentTransactionAppliedOperationResult> appliedOperations
) {
}
