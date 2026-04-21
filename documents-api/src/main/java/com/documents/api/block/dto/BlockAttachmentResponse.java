package com.documents.api.block.dto;

import java.time.Instant;
import java.util.UUID;

import com.documents.service.attachment.BlockAttachmentDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "블록 첨부파일 응답")
public class BlockAttachmentResponse {

    @Schema(description = "첨부파일 ID")
    private String attachmentId;

    @Schema(description = "블록 ID")
    private UUID blockId;

    @Schema(description = "문서 ID")
    private UUID documentId;

    @Schema(description = "원본 파일명")
    private String originalName;

    @Schema(description = "콘텐츠 타입")
    private String contentType;

    @Schema(description = "파일 크기")
    private long size;

    @Schema(description = "리소스 상태")
    private String status;

    @Schema(description = "생성 시각")
    private Instant createdAt;

    @Schema(description = "삭제 시각", nullable = true)
    private Instant deletedAt;

    public static BlockAttachmentResponse from(BlockAttachmentDescriptor descriptor) {
        return BlockAttachmentResponse.builder()
            .attachmentId(descriptor.attachmentId())
            .blockId(descriptor.blockId())
            .documentId(descriptor.documentId())
            .originalName(descriptor.originalName())
            .contentType(descriptor.contentType())
            .size(descriptor.size())
            .status(descriptor.status())
            .createdAt(descriptor.createdAt())
            .deletedAt(descriptor.deletedAt())
            .build();
    }
}
