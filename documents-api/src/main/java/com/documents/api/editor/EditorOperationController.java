package com.documents.api.editor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documents.api.auth.CurrentUserId;
import com.documents.api.code.SuccessCode;
import com.documents.api.document.DocumentTransactionApiMapper;
import com.documents.api.document.dto.DocumentTransactionRequest;
import com.documents.api.document.dto.DocumentTransactionResponse;
import com.documents.api.dto.GlobalResponse;
import com.documents.api.editor.dto.EditorMoveOperationRequest;
import com.documents.api.editor.dto.EditorMoveResourceType;
import com.documents.service.BlockService;
import com.documents.service.DocumentService;
import com.documents.service.DocumentTransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "EditorOperation", description = "에디터 작업 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/editor-operations")
public class EditorOperationController {

    private final DocumentService documentService;
    private final BlockService blockService;
    private final DocumentTransactionService documentTransactionService;
    private final DocumentTransactionApiMapper documentTransactionApiMapper;

    @Operation(summary = "에디터 저장")
    @PostMapping("/documents/{documentId}/save")
    public ResponseEntity<GlobalResponse<DocumentTransactionResponse>> save(
            @PathVariable("documentId") java.util.UUID documentId,
            @Valid @RequestBody DocumentTransactionRequest request,
            @CurrentUserId String userId
    ) {
        DocumentTransactionResponse response = documentTransactionApiMapper.toResponse(
                documentTransactionService.apply(documentId, documentTransactionApiMapper.toCommand(request), userId)
        );
        return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, response));
    }

    @Operation(summary = "에디터 이동")
    @PostMapping("/move")
    public ResponseEntity<GlobalResponse<Void>> move(
            @Valid @RequestBody EditorMoveOperationRequest request,
            @CurrentUserId String userId
    ) {
        if (request.getResourceType() == EditorMoveResourceType.DOCUMENT) {
            documentService.move(
                    request.getResourceId(),
                    request.getTargetParentId(),
                    request.getAfterId(),
                    request.getBeforeId(),
                    userId
            );
            return ResponseEntity.ok(GlobalResponse.ok());
        }

        blockService.move(
                request.getResourceId(),
                request.getTargetParentId(),
                request.getAfterId(),
                request.getBeforeId(),
                request.getVersion(),
                userId
        );
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
