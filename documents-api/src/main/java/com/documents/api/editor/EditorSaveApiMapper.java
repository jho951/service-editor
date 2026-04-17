package com.documents.api.editor;

import org.springframework.stereotype.Component;

import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.editor.dto.EditorSaveAppliedOperationResponse;
import com.documents.api.editor.dto.EditorSaveOperationRequest;
import com.documents.api.editor.dto.EditorSaveRequest;
import com.documents.api.editor.dto.EditorSaveResponse;
import com.documents.service.editor.EditorSaveAppliedOperationResult;
import com.documents.service.editor.EditorSaveCommand;
import com.documents.service.editor.EditorSaveOperationCommand;
import com.documents.service.editor.EditorSaveResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EditorSaveApiMapper {

    private final BlockJsonCodec blockJsonCodec;

    public EditorSaveCommand toCommand(EditorSaveRequest request) {
        return new EditorSaveCommand(
                request.getClientId(),
                request.getBatchId(),
                request.getOperations().stream()
                        .map(this::toCommand)
                        .toList()
        );
    }

    public EditorSaveResponse toResponse(EditorSaveResult result) {
        return EditorSaveResponse.builder()
                .documentId(result.documentId())
                .documentVersion(result.documentVersion())
                .batchId(result.batchId())
                .appliedOperations(result.appliedOperations().stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    private EditorSaveOperationCommand toCommand(EditorSaveOperationRequest request) {
        return new EditorSaveOperationCommand(
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

    private EditorSaveAppliedOperationResponse toResponse(EditorSaveAppliedOperationResult result) {
        return EditorSaveAppliedOperationResponse.builder()
                .opId(result.opId())
                .status(result.status().name())
                .tempId(result.tempId())
                .blockId(result.blockId())
                .version(result.version())
                .sortKey(result.sortKey())
                .deletedAt(result.deletedAt())
                .build();
    }
}
