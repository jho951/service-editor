package com.documents.api.block;

import com.documents.api.block.dto.BlockResponse;
import com.documents.api.block.dto.CreateBlockRequest;
import com.documents.api.code.SuccessCode;
import com.documents.api.dto.GlobalResponse;
import com.documents.domain.Block;
import com.documents.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
                request.getText(),
                request.getAfterBlockId(),
                request.getBeforeBlockId(),
                userId
        );

        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
                .body(GlobalResponse.ok(SuccessCode.CREATED, blockApiMapper.toResponse(createdBlock)));
    }
}
