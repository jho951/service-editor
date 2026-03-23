package com.documents.service.transaction;

import java.util.List;

public record DocumentTransactionCommand(
        String clientId,
        String batchId,
        Integer documentVersion,
        List<DocumentTransactionOperationCommand> operations
) {
    public DocumentTransactionCommand(String clientId, String batchId, List<DocumentTransactionOperationCommand> operations) {
        this(clientId, batchId, 0, operations);
    }
}
