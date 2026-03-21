package com.documents.api.block;

import com.documents.api.block.dto.BlockResponse;
import com.documents.api.block.dto.CreateBlockRequest;
import com.documents.api.block.dto.MoveBlockRequest;
import com.documents.api.block.dto.UpdateBlockRequest;
import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.code.SuccessCode;
import com.documents.api.dto.GlobalResponse;
import com.documents.domain.Block;
import com.documents.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Block", description = "블록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class BlockController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final BlockService blockService;
    private final BlockApiMapper blockApiMapper;
    private final BlockJsonCodec blockJsonCodec;

    @Operation(summary = "문서 블록 목록 조회")
    @GetMapping("/documents/{documentId}/blocks")
    public ResponseEntity<GlobalResponse<List<BlockResponse>>> getBlocks(
            @PathVariable("documentId") UUID documentId
    ) {
        List<BlockResponse> response = blockService.getAllByDocumentId(documentId).stream()
                .map(blockApiMapper::toResponse)
                .toList();
        return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, response));
    }

    @Operation(summary = "텍스트 블록 생성")
    @PostMapping("/documents/{documentId}/blocks")
    public ResponseEntity<GlobalResponse<BlockResponse>> createBlock(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody CreateBlockRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        Block createdBlock = blockService.create(
                documentId,
                request.getParentId(),
                request.getType(),
                blockJsonCodec.write(request.getContent()),
                request.getAfterBlockId(),
                request.getBeforeBlockId(),
                userId
        );

        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
                .body(GlobalResponse.ok(SuccessCode.CREATED, blockApiMapper.toResponse(createdBlock)));
    }

    @Operation(summary = "블록 수정")
    @PatchMapping("/blocks/{blockId}")
    public ResponseEntity<GlobalResponse<BlockResponse>> updateBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody UpdateBlockRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        Block updatedBlock = blockService.update(
                blockId,
                blockJsonCodec.write(request.getContent()),
                request.getVersion(),
                userId
        );
        return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, blockApiMapper.toResponse(updatedBlock)));
    }

    @Operation(summary = "블록 삭제")
    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<GlobalResponse<Void>> deleteBlock(
            @PathVariable("blockId") UUID blockId,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        blockService.delete(blockId, userId);
        return ResponseEntity.ok(GlobalResponse.ok());
    }

    @Operation(summary = "블록 이동")
    @PostMapping("/blocks/{blockId}/move")
    public ResponseEntity<GlobalResponse<Void>> moveBlock(
            @PathVariable("blockId") UUID blockId,
            @Valid @RequestBody MoveBlockRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        blockService.move(
                blockId,
                request.getParentId(),
                request.getAfterBlockId(),
                request.getBeforeBlockId(),
                request.getVersion(),
                userId
        );
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
