package com.documents.service.transaction;

import java.util.UUID;

public record DocumentTransactionAppliedOperationResult(
        String opId,
        DocumentTransactionOperationStatus status,
        String tempId,
        UUID blockId,
        Integer version,
        String sortKey
) {
}
