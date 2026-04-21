package com.documents.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.documents.domain.Block;
import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.snapshot.DocumentSnapshotContent;
import com.documents.service.snapshot.DocumentSnapshotDescriptor;
import com.documents.support.TextNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jho951.platform.resource.api.ResourceAccessException;
import io.github.jho951.platform.resource.api.ResourceDescriptor;
import io.github.jho951.platform.resource.api.ResourceId;
import io.github.jho951.platform.resource.api.ResourceNotFoundException;
import io.github.jho951.platform.resource.api.ResourcePrincipal;
import io.github.jho951.platform.resource.api.ResourceService;
import io.github.jho951.platform.resource.api.ResourceStoreRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DocumentSnapshotServiceImpl implements DocumentSnapshotService {

    private static final String DOCUMENT_KIND = "document-snapshot";
    private static final String CONTENT_TYPE = "application/json";
    private static final String DOCUMENT_ID_ATTRIBUTE = "documentId";
    private static final String DOCUMENT_VERSION_ATTRIBUTE = "documentVersion";
    private static final String OWNER_USER_ID_ATTRIBUTE = "ownerUserId";
    private static final String SNAPSHOT_CREATED_BY_ATTRIBUTE = "snapshotCreatedBy";

    private final BlockService blockService;
    private final DocumentResourceBindingService documentResourceBindingService;
    private final ResourceService resourceService;
    private final TextNormalizer textNormalizer;
    private final ObjectMapper objectMapper;
    private final DocumentAccessGuard documentAccessGuard;
    private final DocumentsResourceOwnerResolver documentsResourceOwnerResolver;
    private final DocumentsResourcePrincipalFactory documentsResourcePrincipalFactory;

    public DocumentSnapshotServiceImpl(
        BlockService blockService,
        DocumentResourceBindingService documentResourceBindingService,
        ResourceService resourceService,
        TextNormalizer textNormalizer,
        ObjectMapper objectMapper,
        DocumentAccessGuard documentAccessGuard,
        DocumentsResourceOwnerResolver documentsResourceOwnerResolver,
        DocumentsResourcePrincipalFactory documentsResourcePrincipalFactory
    ) {
        this.blockService = blockService;
        this.documentResourceBindingService = documentResourceBindingService;
        this.resourceService = resourceService;
        this.textNormalizer = textNormalizer;
        this.objectMapper = objectMapper;
        this.documentAccessGuard = documentAccessGuard;
        this.documentsResourceOwnerResolver = documentsResourceOwnerResolver;
        this.documentsResourcePrincipalFactory = documentsResourcePrincipalFactory;
    }

    @Override
    @Transactional
    public DocumentSnapshotDescriptor create(UUID documentId, String actorId) {
        Document document = documentAccessGuard.requireWritable(documentId, actorId);
        String normalizedActorId = normalizeActorId(actorId);
        String ownerUserId = document.getCreatedBy();
        byte[] content = serializeSnapshot(document, blockService.getAllByDocumentId(documentId));

        ResourceDescriptor descriptor = resourceService.store(new ResourceStoreRequest(
            documentsResourceOwnerResolver.forDocument(document),
            DOCUMENT_KIND,
            originalName(document),
            CONTENT_TYPE,
            (long) content.length,
            Map.of(
                DOCUMENT_ID_ATTRIBUTE, document.getId().toString(),
                DOCUMENT_VERSION_ATTRIBUTE, documentVersion(document),
                OWNER_USER_ID_ATTRIBUTE, ownerUserId,
                SNAPSHOT_CREATED_BY_ATTRIBUTE, normalizedActorId
            ),
            new ByteArrayInputStream(content)
        ));
        documentResourceBindingService.bindSnapshot(document, descriptor.id().value(), normalizedActorId, ownerUserId);
        return toDescriptor(descriptor, documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentSnapshotDescriptor describe(UUID documentId, String snapshotId, String actorId) {
        documentAccessGuard.requireWritable(documentId, actorId);
        return toDescriptor(loadDescriptor(documentId, snapshotId, actorId), documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentSnapshotContent open(UUID documentId, String snapshotId, String actorId) {
        documentAccessGuard.requireWritable(documentId, actorId);
        ResourceDescriptor descriptor = loadDescriptor(documentId, snapshotId, actorId);
        InputStream input = openContent(snapshotId, actorId);
        return new DocumentSnapshotContent(
            descriptor.id().value(),
            documentId,
            extractDocumentVersion(descriptor),
            descriptor.metadata().originalName(),
            descriptor.metadata().contentType(),
            descriptor.metadata().size(),
            input
        );
    }

    @Override
    @Transactional
    public void delete(UUID documentId, String snapshotId, String actorId) {
        documentAccessGuard.requireWritable(documentId, actorId);
        loadDescriptor(documentId, snapshotId, actorId);
        documentResourceBindingService.scheduleSnapshotPurge(documentId, snapshotId, normalizeActorId(actorId));
    }

    private byte[] serializeSnapshot(Document document, List<Block> blocks) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document", documentPayload(document));
        payload.put("blocks", blocks.stream().map(this::blockPayload).toList());

        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException("Failed to serialize document snapshot", ex);
        }
    }

    private Map<String, Object> documentPayload(Document document) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", document.getId());
        payload.put("parentId", document.getParentId());
        payload.put("title", document.getTitle());
        payload.put("icon", readNullableJson(document.getIconJson()));
        payload.put("cover", readNullableJson(document.getCoverJson()));
        payload.put("visibility", document.getVisibility());
        payload.put("sortKey", document.getSortKey());
        payload.put("version", document.getVersion());
        payload.put("createdBy", document.getCreatedBy());
        payload.put("updatedBy", document.getUpdatedBy());
        payload.put("createdAt", document.getCreatedAt());
        payload.put("updatedAt", document.getUpdatedAt());
        payload.put("deletedAt", document.getDeletedAt());
        return payload;
    }

    private Map<String, Object> blockPayload(Block block) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", block.getId());
        payload.put("documentId", block.getDocumentId());
        payload.put("parentId", block.getParentId());
        payload.put("type", block.getType());
        payload.put("content", readRequiredJson(block.getContent()));
        payload.put("sortKey", block.getSortKey());
        payload.put("version", block.getVersion());
        payload.put("createdBy", block.getCreatedBy());
        payload.put("updatedBy", block.getUpdatedBy());
        payload.put("createdAt", block.getCreatedAt());
        payload.put("updatedAt", block.getUpdatedAt());
        payload.put("deletedAt", block.getDeletedAt());
        return payload;
    }

    private Object readNullableJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return readRequiredJson(value);
    }

    private Object readRequiredJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException("Failed to deserialize document snapshot payload", ex);
        }
    }

    private ResourceDescriptor loadDescriptor(UUID documentId, String snapshotId, String actorId) {
        documentResourceBindingService.requireActiveSnapshotBinding(documentId, snapshotId);

        try {
            ResourceDescriptor descriptor = resourceService.describe(
                principal(actorId),
                resourceId(snapshotId)
            );
            validateSnapshotBelongsToDocument(descriptor, documentId);
            return descriptor;
        } catch (ResourceNotFoundException | ResourceAccessException ex) {
            throw new BusinessException(BusinessErrorCode.DOCUMENT_SNAPSHOT_NOT_FOUND);
        }
    }

    private InputStream openContent(String snapshotId, String actorId) {
        try {
            return resourceService.open(principal(actorId), resourceId(snapshotId));
        } catch (ResourceNotFoundException | ResourceAccessException ex) {
            throw new BusinessException(BusinessErrorCode.DOCUMENT_SNAPSHOT_NOT_FOUND);
        }
    }

    private void validateSnapshotBelongsToDocument(ResourceDescriptor descriptor, UUID documentId) {
        String attachedDocumentId = descriptor.attributes().get(DOCUMENT_ID_ATTRIBUTE);
        if (!documentId.toString().equals(attachedDocumentId)) {
            throw new BusinessException(BusinessErrorCode.DOCUMENT_SNAPSHOT_NOT_FOUND);
        }
    }

    private long extractDocumentVersion(ResourceDescriptor descriptor) {
        String documentVersion = descriptor.attributes().get(DOCUMENT_VERSION_ATTRIBUTE);
        if (!StringUtils.hasText(documentVersion)) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
        return Long.parseLong(documentVersion);
    }

    private DocumentSnapshotDescriptor toDescriptor(ResourceDescriptor descriptor, UUID documentId) {
        return new DocumentSnapshotDescriptor(
            descriptor.id().value(),
            documentId,
            extractDocumentVersion(descriptor),
            descriptor.metadata().originalName(),
            descriptor.metadata().contentType(),
            descriptor.metadata().size(),
            descriptor.status().name(),
            descriptor.createdAt(),
            descriptor.deletedAt()
        );
    }

    private String normalizeActorId(String actorId) {
        String normalizedActorId = textNormalizer.normalizeNullable(actorId);
        if (normalizedActorId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
        return normalizedActorId;
    }

    private String originalName(Document document) {
        return "document-%s-v%s-snapshot.json".formatted(document.getId(), documentVersion(document));
    }

    private String documentVersion(Document document) {
        return document.getVersion() == null ? "0" : document.getVersion().toString();
    }

    private ResourcePrincipal principal(String actorId) {
        return documentsResourcePrincipalFactory.create(actorId);
    }

    private ResourceId resourceId(String snapshotId) {
        return new ResourceId(snapshotId);
    }
}
