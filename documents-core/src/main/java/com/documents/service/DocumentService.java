package com.documents.service;

import com.documents.domain.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentService {
    Document create(Document document);
    Optional<Document> findById(UUID id);
    List<Document> findPage(int offset, int limit);
    Document update(UUID id, Document document);
    void delete(UUID id);
}
