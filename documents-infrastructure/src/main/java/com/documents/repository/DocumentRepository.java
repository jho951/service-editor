package com.documents.repository;

import com.documents.domain.Document;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
