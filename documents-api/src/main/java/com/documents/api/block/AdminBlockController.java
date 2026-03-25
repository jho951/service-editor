package com.documents.api.block;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documents.api.code.SuccessCode;
import com.documents.api.document.DocumentTransactionApiMapper;
import com.documents.api.document.dto.DocumentTransactionRequest;
import com.documents.api.document.dto.DocumentTransactionResponse;
import com.documents.api.dto.GlobalResponse;
import com.documents.service.AdminBlockTransactionService;
import com.documents.service.transaction.DocumentTransactionCommand;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "AdminBlock", description = "관리자 블록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin")
public class AdminBlockController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AdminBlockTransactionService adminBlockTransactionService;
    private final DocumentTransactionApiMapper documentTransactionApiMapper;

    @Operation(summary = "텍스트 블록 생성")
    @PostMapping("/documents/{documentId}/blocks")
    public ResponseEntity<GlobalResponse<DocumentTransactionResponse>> createBlock(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody DocumentTransactionRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        DocumentTransactionCommand command = documentTransactionApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                documentTransactionApiMapper.toResponse(
                        adminBlockTransactionService.applyCreate(documentId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }

    @Operation(summary = "블록 수정")
    @PatchMapping("/blocks/{blockId}")
    public ResponseEntity<GlobalResponse<DocumentTransactionResponse>> updateBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody DocumentTransactionRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        DocumentTransactionCommand command = documentTransactionApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                documentTransactionApiMapper.toResponse(
                        adminBlockTransactionService.applyReplaceContent(blockId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }

    @Operation(summary = "블록 삭제")
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<GlobalResponse<DocumentTransactionResponse>> deleteBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody DocumentTransactionRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        DocumentTransactionCommand command = documentTransactionApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                documentTransactionApiMapper.toResponse(
                        adminBlockTransactionService.applyDelete(blockId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }

    @Operation(summary = "블록 이동")
    @PostMapping("/blocks/{blockId}/move")
    public ResponseEntity<GlobalResponse<DocumentTransactionResponse>> moveBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody DocumentTransactionRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        DocumentTransactionCommand command = documentTransactionApiMapper.toCommand(request);

        return ResponseEntity.ok(GlobalResponse.ok(
                SuccessCode.SUCCESS,
                documentTransactionApiMapper.toResponse(
                        adminBlockTransactionService.applyMove(blockId, command.batchId(), command.operations().get(0), userId)
                )
        ));
    }
}
