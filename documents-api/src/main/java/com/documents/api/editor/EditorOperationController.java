package com.documents.api.editor;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documents.api.auth.CurrentUserId;
import com.documents.api.code.SuccessCode;
import com.documents.api.dto.GlobalResponse;
import com.documents.api.editor.dto.EditorMoveOperationRequest;
import com.documents.api.editor.dto.EditorMoveResourceType;
import com.documents.api.editor.dto.EditorSaveRequest;
import com.documents.api.editor.dto.EditorSaveResponse;
import com.documents.service.BlockService;
import com.documents.service.DocumentService;
import com.documents.service.EditorOperationOrchestrator;

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
    private final EditorOperationOrchestrator editorOperationOrchestrator;
    private final EditorSaveApiMapper editorSaveApiMapper;

    @Operation(summary = "에디터 저장")
    @PostMapping("/documents/{documentId}/save")
    public ResponseEntity<GlobalResponse<EditorSaveResponse>> save(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody EditorSaveRequest request,
            @CurrentUserId String userId
    ) {
        EditorSaveResponse response = editorSaveApiMapper.toResponse(
                editorOperationOrchestrator.save(documentId, editorSaveApiMapper.toCommand(request), userId)
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
