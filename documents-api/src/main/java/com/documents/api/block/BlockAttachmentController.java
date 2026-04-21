package com.documents.api.block;

import java.io.IOException;
import java.util.UUID;

import com.documents.api.auth.CurrentUserId;
import com.documents.api.code.SuccessCode;
import com.documents.api.dto.GlobalResponse;
import com.documents.api.block.dto.BlockAttachmentResponse;
import com.documents.service.BlockAttachmentService;
import com.documents.service.attachment.BlockAttachmentContent;
import com.documents.service.attachment.BlockAttachmentDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "BlockAttachment", description = "블록 첨부파일 API")
@RestController
@RequestMapping("/blocks")
public class BlockAttachmentController {

    private final BlockAttachmentService blockAttachmentService;

    public BlockAttachmentController(BlockAttachmentService blockAttachmentService) {
        this.blockAttachmentService = blockAttachmentService;
    }

    @Operation(summary = "블록 첨부파일 업로드")
    @PostMapping(path = "/{blockId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GlobalResponse<BlockAttachmentResponse>> uploadAttachment(
        @PathVariable("blockId") UUID blockId,
        @RequestPart("file") MultipartFile file,
        @CurrentUserId String userId
    ) throws IOException {
        BlockAttachmentDescriptor descriptor = blockAttachmentService.store(
            blockId,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize(),
            file.getInputStream(),
            userId
        );
        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
            .body(GlobalResponse.ok(SuccessCode.CREATED, BlockAttachmentResponse.from(descriptor)));
    }

    @Operation(summary = "블록 첨부파일 메타데이터 조회")
    @GetMapping("/{blockId}/attachments/{attachmentId}")
    public ResponseEntity<GlobalResponse<BlockAttachmentResponse>> getAttachment(
        @PathVariable("blockId") UUID blockId,
        @PathVariable("attachmentId") String attachmentId,
        @CurrentUserId String userId
    ) {
        BlockAttachmentDescriptor descriptor = blockAttachmentService.describe(blockId, attachmentId, userId);
        return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, BlockAttachmentResponse.from(descriptor)));
    }

    @Operation(summary = "블록 첨부파일 다운로드")
    @GetMapping("/{blockId}/attachments/{attachmentId}/content")
    public ResponseEntity<InputStreamResource> downloadAttachment(
        @PathVariable("blockId") UUID blockId,
        @PathVariable("attachmentId") String attachmentId,
        @CurrentUserId String userId
    ) {
        BlockAttachmentContent content = blockAttachmentService.open(blockId, attachmentId, userId);
        MediaType mediaType = MediaType.parseMediaType(content.contentType());
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(content.size())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(content.originalName()).build().toString()
            )
            .body(new InputStreamResource(content.input()));
    }

    @Operation(summary = "블록 첨부파일 삭제")
    @DeleteMapping("/{blockId}/attachments/{attachmentId}")
    public ResponseEntity<GlobalResponse<Void>> deleteAttachment(
        @PathVariable("blockId") UUID blockId,
        @PathVariable("attachmentId") String attachmentId,
        @CurrentUserId String userId
    ) {
        blockAttachmentService.delete(blockId, attachmentId, userId);
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
