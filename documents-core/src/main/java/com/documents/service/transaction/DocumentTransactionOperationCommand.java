package com.documents.service.transaction;

import java.util.UUID;

public record DocumentTransactionOperationCommand(
        String opId,
        DocumentTransactionOperationType type,
        String blockReference,
        Integer version,
        String content,
        UUID parentId,
        UUID afterBlockId,
        UUID beforeBlockId
) {
}
