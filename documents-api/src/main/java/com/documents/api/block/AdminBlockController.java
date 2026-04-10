package com.documents.api.block;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documents.api.auth.CurrentUserId;
import com.documents.api.code.SuccessCode;
import com.documents.api.dto.GlobalResponse;
import com.documents.api.editor.EditorSaveApiMapper;
import com.documents.api.editor.dto.EditorSaveRequest;
import com.documents.api.editor.dto.EditorSaveResponse;
import com.documents.service.AdminBlockOperationService;
import com.documents.service.editor.EditorSaveCommand;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "AdminBlock", description = "관리자 블록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminBlockController {

    private final AdminBlockOperationService adminBlockOperationService;
    private final EditorSaveApiMapper editorSaveApiMapper;

    @Operation(summary = "텍스트 블록 생성")
    @PostMapping("/documents/{documentId}/blocks")
    public ResponseEntity<GlobalResponse<EditorSaveResponse>> createBlock(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody EditorSaveRequest request,
            @CurrentUserId String userId
    ) {
        EditorSaveCommand command = editorSaveApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                editorSaveApiMapper.toResponse(
                        adminBlockOperationService.applyCreate(documentId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }

    @Operation(summary = "블록 수정")
    @PatchMapping("/blocks/{blockId}")
    public ResponseEntity<GlobalResponse<EditorSaveResponse>> updateBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody EditorSaveRequest request,
            @CurrentUserId String userId
    ) {
        EditorSaveCommand command = editorSaveApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                editorSaveApiMapper.toResponse(
                        adminBlockOperationService.applyReplaceContent(blockId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }

    @Operation(summary = "블록 삭제")
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<GlobalResponse<EditorSaveResponse>> deleteBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody EditorSaveRequest request,
            @CurrentUserId String userId
    ) {
        EditorSaveCommand command = editorSaveApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                editorSaveApiMapper.toResponse(
                        adminBlockOperationService.applyDelete(blockId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }

    @Operation(summary = "블록 이동")
    @PostMapping("/blocks/{blockId}/move")
    public ResponseEntity<GlobalResponse<EditorSaveResponse>> moveBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody EditorSaveRequest request,
            @CurrentUserId String userId
    ) {
        EditorSaveCommand command = editorSaveApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                editorSaveApiMapper.toResponse(
                        adminBlockOperationService.applyMove(blockId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }
}
