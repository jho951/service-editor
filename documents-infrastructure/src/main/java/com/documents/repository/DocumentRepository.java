package com.documents.repository;

import com.documents.domain.Document;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);
}
