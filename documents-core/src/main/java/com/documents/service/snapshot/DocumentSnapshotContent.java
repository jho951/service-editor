package com.documents.service.snapshot;

import java.io.InputStream;
import java.util.UUID;

public record DocumentSnapshotContent(
    String snapshotId,
    UUID documentId,
    long documentVersion,
    String originalName,
    String contentType,
    long size,
    InputStream input
) {
}
