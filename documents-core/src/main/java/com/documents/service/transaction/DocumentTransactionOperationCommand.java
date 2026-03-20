package com.documents.service.transaction;

public record DocumentTransactionOperationCommand(
        String opId,
        DocumentTransactionOperationType type,
        String blockReference,
        Integer version,
        String content,
        String parentReference,
        String afterReference,
        String beforeReference
) {
}
