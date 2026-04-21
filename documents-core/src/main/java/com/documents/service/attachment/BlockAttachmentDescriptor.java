package com.documents.service.attachment;

import java.time.Instant;
import java.util.UUID;

public record BlockAttachmentDescriptor(
    String attachmentId,
    UUID blockId,
    UUID documentId,
    String originalName,
    String contentType,
    long size,
    String status,
    Instant createdAt,
    Instant deletedAt
) {
}
