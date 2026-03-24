package com.documents.api.document;

import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.document.dto.DocumentTransactionAppliedOperationResponse;
import com.documents.api.document.dto.DocumentTransactionOperationRequest;
import com.documents.api.document.dto.DocumentTransactionRequest;
import com.documents.api.document.dto.DocumentTransactionResponse;
import com.documents.service.transaction.DocumentTransactionAppliedOperationResult;
import com.documents.service.transaction.DocumentTransactionCommand;
import com.documents.service.transaction.DocumentTransactionOperationCommand;
import com.documents.service.transaction.DocumentTransactionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentTransactionApiMapper {

    private final BlockJsonCodec blockJsonCodec;

    public DocumentTransactionCommand toCommand(DocumentTransactionRequest request) {
        return new DocumentTransactionCommand(
                request.getClientId(),
                request.getBatchId(),
                request.getOperations().stream()
                        .map(this::toCommand)
                        .toList()
        );
    }

    public DocumentTransactionResponse toResponse(DocumentTransactionResult result) {
        Long documentVersion = result.documentVersion() == null ? null : result.documentVersion().longValue();
        return DocumentTransactionResponse.builder()
                .documentId(result.documentId())
                .documentVersion(documentVersion)
                .batchId(result.batchId())
                .appliedOperations(result.appliedOperations().stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    private DocumentTransactionOperationCommand toCommand(DocumentTransactionOperationRequest request) {
        return new DocumentTransactionOperationCommand(
                request.getOpId(),
                request.getType(),
                request.getBlockRef(),
                request.getVersion(),
                blockJsonCodec.write(request.getContent()),
                request.getParentRef(),
                request.getAfterRef(),
                request.getBeforeRef()
        );
    }

    private DocumentTransactionAppliedOperationResponse toResponse(DocumentTransactionAppliedOperationResult result) {
        Long version = result.version() == null ? null : result.version().longValue();
        return DocumentTransactionAppliedOperationResponse.builder()
                .opId(result.opId())
                .status(result.status().name())
                .tempId(result.tempId())
                .blockId(result.blockId())
                .version(version)
                .sortKey(result.sortKey())
                .deletedAt(result.deletedAt())
                .build();
    }
}
