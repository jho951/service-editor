package com.documents.service;

import com.documents.domain.Document;
import com.documents.domain.DocumentVisibility;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    Document create(UUID parentId, String title, String iconJson, String coverJson, String actorId);
    List<Document> getAllByUserId(String userId);
    List<Document> getTrashByUserId(String userId);
    Document getById(UUID documentId);
    Document update(UUID documentId, String title, String iconJson, String coverJson, Integer version, String actorId);
    Document updateVisibility(UUID documentId, DocumentVisibility visibility, Integer version, String actorId);
    void delete(UUID documentId, String actorId);
    void trash(UUID documentId, String actorId);
    void restore(UUID documentId, String actorId);
    void purgeExpiredTrash();
    Document move(UUID documentId, UUID targetParentId, UUID afterDocumentId, UUID beforeDocumentId, String actorId);
}
