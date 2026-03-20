package com.documents.service;

import com.documents.service.transaction.DocumentTransactionCommand;
import com.documents.service.transaction.DocumentTransactionResult;
import java.util.UUID;

public interface DocumentTransactionService {

    DocumentTransactionResult apply(UUID documentId, DocumentTransactionCommand command, String actorId);
}
