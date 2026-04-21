package com.documents.service;

import java.util.UUID;

import com.documents.domain.Document;

public interface DocumentAccessGuard {
    Document requireReadable(UUID documentId, String actorId);
    Document requireWritable(UUID documentId, String actorId);
}
