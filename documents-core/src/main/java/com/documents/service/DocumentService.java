package com.documents.service;

import com.documents.domain.Document;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    Document create(UUID workspaceId, UUID parentId, String title, String iconJson, String coverJson, String actorId);
    List<Document> getAllByWorkspaceId(UUID workspaceId);
    Document getById(UUID documentId);
}
