package com.documents.service.transaction;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentTransactionAppliedOperationResult(
        String opId,
        DocumentTransactionOperationStatus status,
        String tempId,
        UUID blockId,
        Integer version,
        String sortKey,
        LocalDateTime deletedAt
) {
}
