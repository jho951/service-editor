package com.documents.service;

import java.util.UUID;

import com.documents.service.transaction.DocumentTransactionOperationCommand;
import com.documents.service.transaction.DocumentTransactionResult;

public interface AdminBlockTransactionService {

    DocumentTransactionResult applyCreate(
            UUID documentId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    );

    DocumentTransactionResult applyReplaceContent(
            UUID blockId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    );

    DocumentTransactionResult applyMove(
            UUID blockId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    );

    DocumentTransactionResult applyDelete(
            UUID blockId,
            String batchId,
            DocumentTransactionOperationCommand operation,
            String actorId
    );
}
