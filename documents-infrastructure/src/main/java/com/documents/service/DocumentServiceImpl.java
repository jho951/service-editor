package com.documents.service;

import com.documents.domain.Document;
import com.documents.repository.DocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public Document create(Document document) {
        if (document.getId() == null) {
            document.setId(UUID.randomUUID());
        }
        return documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Document> findById(UUID id) {
        return documentRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findPage(int offset, int limit) {
        int pageSize = Math.max(limit, 1);
        int pageIndex = Math.max(offset, 0) / pageSize;
        return documentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(pageIndex, pageSize))
                .getContent();
    }

    @Override
    @Transactional
    public Document update(UUID id, Document document) {
        Document persisted = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        if (document.getTitle() != null) {
            persisted.setTitle(document.getTitle());
        }
        if (document.getWidth() != null) {
            persisted.setWidth(document.getWidth());
        }
        if (document.getHeight() != null) {
            persisted.setHeight(document.getHeight());
        }
        if (document.getVectorJson() != null) {
            persisted.setVectorJson(document.getVectorJson());
        }

        return documentRepository.save(persisted);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        documentRepository.deleteById(id);
    }
}
