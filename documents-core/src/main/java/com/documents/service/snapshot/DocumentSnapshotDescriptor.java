package com.documents.service.snapshot;

import java.time.Instant;
import java.util.UUID;

public record DocumentSnapshotDescriptor(
    String snapshotId,
    UUID documentId,
    long documentVersion,
    String originalName,
    String contentType,
    long size,
    String status,
    Instant createdAt,
    Instant deletedAt
) {
}
