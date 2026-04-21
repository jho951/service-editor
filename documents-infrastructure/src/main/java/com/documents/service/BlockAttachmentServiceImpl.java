package com.documents.service;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import com.documents.domain.Block;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.attachment.BlockAttachmentContent;
import com.documents.service.attachment.BlockAttachmentDescriptor;
import com.documents.support.TextNormalizer;
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
public class BlockAttachmentServiceImpl implements BlockAttachmentService {

    private static final String ATTACHMENT_KIND = "editor-attachment";
    private static final String OWNER_TYPE = "USER";
    private static final String BLOCK_ID_ATTRIBUTE = "blockId";
    private static final String DOCUMENT_ID_ATTRIBUTE = "documentId";
    private static final String OWNER_USER_ID_ATTRIBUTE = "ownerUserId";
    private static final String UPLOADED_BY_ATTRIBUTE = "uploadedBy";

    private final BlockAccessGuard blockAccessGuard;
    private final DocumentResourceBindingService documentResourceBindingService;
    private final ResourceService resourceService;
    private final TextNormalizer textNormalizer;
    private final DocumentsResourceOwnerResolver documentsResourceOwnerResolver;
    private final DocumentsResourcePrincipalFactory documentsResourcePrincipalFactory;

    public BlockAttachmentServiceImpl(
        BlockAccessGuard blockAccessGuard,
        DocumentResourceBindingService documentResourceBindingService,
        ResourceService resourceService,
        TextNormalizer textNormalizer,
        DocumentsResourceOwnerResolver documentsResourceOwnerResolver,
        DocumentsResourcePrincipalFactory documentsResourcePrincipalFactory
    ) {
        this.blockAccessGuard = blockAccessGuard;
        this.documentResourceBindingService = documentResourceBindingService;
        this.resourceService = resourceService;
        this.textNormalizer = textNormalizer;
        this.documentsResourceOwnerResolver = documentsResourceOwnerResolver;
        this.documentsResourcePrincipalFactory = documentsResourcePrincipalFactory;
    }

    @Override
    @Transactional
    public BlockAttachmentDescriptor store(
        UUID blockId,
        String originalName,
        String contentType,
        Long contentLength,
        InputStream input,
        String actorId
    ) {
        Block block = blockAccessGuard.requireWritable(blockId, actorId);
        String normalizedActorId = normalizeActorId(actorId);
        String ownerUserId = block.getDocument().getCreatedBy();

        ResourceDescriptor descriptor = resourceService.store(new ResourceStoreRequest(
            documentsResourceOwnerResolver.forDocument(block.getDocument()),
            ATTACHMENT_KIND,
            normalizeOriginalName(originalName),
            normalizeContentType(contentType),
            contentLength,
            Map.of(
                BLOCK_ID_ATTRIBUTE, block.getId().toString(),
                DOCUMENT_ID_ATTRIBUTE, block.getDocumentId().toString(),
                OWNER_USER_ID_ATTRIBUTE, ownerUserId,
                UPLOADED_BY_ATTRIBUTE, normalizedActorId
            ),
            input
        ));
        documentResourceBindingService.bindAttachment(block, descriptor.id().value(), normalizedActorId, ownerUserId);
        return toDescriptor(descriptor, blockId);
    }

    @Override
    @Transactional(readOnly = true)
    public BlockAttachmentDescriptor describe(UUID blockId, String attachmentId, String actorId) {
        blockAccessGuard.requireReadable(blockId, actorId);
        return toDescriptor(loadDescriptor(blockId, attachmentId, actorId), blockId);
    }

    @Override
    @Transactional(readOnly = true)
    public BlockAttachmentContent open(UUID blockId, String attachmentId, String actorId) {
        blockAccessGuard.requireReadable(blockId, actorId);
        ResourceDescriptor descriptor = loadDescriptor(blockId, attachmentId, actorId);
        InputStream input = openContent(attachmentId, actorId);
        return new BlockAttachmentContent(
            descriptor.id().value(),
            blockId,
            extractDocumentId(descriptor),
            descriptor.metadata().originalName(),
            descriptor.metadata().contentType(),
            descriptor.metadata().size(),
            input
        );
    }

    @Override
    @Transactional
    public void delete(UUID blockId, String attachmentId, String actorId) {
        Block block = blockAccessGuard.requireWritable(blockId, actorId);
        String normalizedActorId = normalizeActorId(actorId);
        loadDescriptor(blockId, attachmentId, actorId);
        documentResourceBindingService.scheduleAttachmentPurge(
            block.getDocumentId(),
            block.getId(),
            attachmentId,
            normalizedActorId
        );
    }

    private ResourceDescriptor loadDescriptor(UUID blockId, String attachmentId, String actorId) {
        Block block = blockAccessGuard.requireReadable(blockId, actorId);
        documentResourceBindingService.requireActiveAttachmentBinding(block.getDocumentId(), block.getId(), attachmentId);

        try {
            ResourceDescriptor descriptor = resourceService.describe(
                principal(actorId),
                resourceId(attachmentId)
            );
            validateAttachmentBelongsToBlock(descriptor, blockId);
            return descriptor;
        } catch (ResourceNotFoundException | ResourceAccessException ex) {
            throw new BusinessException(BusinessErrorCode.ATTACHMENT_NOT_FOUND);
        }
    }

    private InputStream openContent(String attachmentId, String actorId) {
        try {
            return resourceService.open(principal(actorId), resourceId(attachmentId));
        } catch (ResourceNotFoundException | ResourceAccessException ex) {
            throw new BusinessException(BusinessErrorCode.ATTACHMENT_NOT_FOUND);
        }
    }

    private void validateAttachmentBelongsToBlock(ResourceDescriptor descriptor, UUID blockId) {
        String attachedBlockId = descriptor.attributes().get(BLOCK_ID_ATTRIBUTE);
        if (!blockId.toString().equals(attachedBlockId)) {
            throw new BusinessException(BusinessErrorCode.ATTACHMENT_NOT_FOUND);
        }
    }

    private UUID extractDocumentId(ResourceDescriptor descriptor) {
        String documentId = descriptor.attributes().get(DOCUMENT_ID_ATTRIBUTE);
        if (!StringUtils.hasText(documentId)) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
        return UUID.fromString(documentId);
    }

    private BlockAttachmentDescriptor toDescriptor(ResourceDescriptor descriptor, UUID blockId) {
        return new BlockAttachmentDescriptor(
            descriptor.id().value(),
            blockId,
            extractDocumentId(descriptor),
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

    private String normalizeOriginalName(String originalName) {
        String normalizedOriginalName = textNormalizer.normalizeNullable(originalName);
        if (normalizedOriginalName == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
        }
        return normalizedOriginalName;
    }

    private String normalizeContentType(String contentType) {
        String normalizedContentType = textNormalizer.normalizeNullable(contentType);
        return normalizedContentType == null ? "application/octet-stream" : normalizedContentType;
    }

    private ResourcePrincipal principal(String actorId) {
        return documentsResourcePrincipalFactory.create(actorId);
    }

    private ResourceId resourceId(String attachmentId) {
        return new ResourceId(attachmentId);
    }
}
