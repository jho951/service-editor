package com.documents.api.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.documents.service.snapshot.DocumentSnapshotDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문서 스냅샷 응답")
public record DocumentSnapshotResponse(
    @Schema(description = "스냅샷 ID")
    String snapshotId,
    @Schema(description = "문서 ID")
    UUID documentId,
    @Schema(description = "문서 버전")
    long documentVersion,
    @Schema(description = "원본 파일명")
    String originalName,
    @Schema(description = "컨텐츠 타입")
    String contentType,
    @Schema(description = "바이트 크기")
    long size,
    @Schema(description = "리소스 상태")
    String status,
    @Schema(description = "생성 시각")
    Instant createdAt,
    @Schema(description = "삭제 시각", nullable = true)
    Instant deletedAt
) {
    public static DocumentSnapshotResponse from(DocumentSnapshotDescriptor descriptor) {
        return new DocumentSnapshotResponse(
            descriptor.snapshotId(),
            descriptor.documentId(),
            descriptor.documentVersion(),
            descriptor.originalName(),
            descriptor.contentType(),
            descriptor.size(),
            descriptor.status(),
            descriptor.createdAt(),
            descriptor.deletedAt()
        );
    }
}
