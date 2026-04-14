package com.documents.service.transaction;

import com.documents.domain.Document;
import java.time.LocalDateTime;
import java.util.UUID;

public interface DocumentVersionUpdater {
    Document increment(UUID documentId, String actorId, LocalDateTime updatedAt);
}
