package com.documents.service.transaction;

import java.util.List;

public record DocumentTransactionCommand(
        String clientId,
        String batchId,
        List<DocumentTransactionOperationCommand> operations
) {}
