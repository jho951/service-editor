package com.documents.service.attachment;

import java.io.InputStream;
import java.util.UUID;

public record BlockAttachmentContent(
    String attachmentId,
    UUID blockId,
    UUID documentId,
    String originalName,
    String contentType,
    long size,
    InputStream input
) {
}
