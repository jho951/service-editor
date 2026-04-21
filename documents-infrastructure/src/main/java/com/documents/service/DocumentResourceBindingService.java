package com.documents.service;

import com.documents.domain.Block;
import com.documents.domain.Document;
import com.documents.domain.DocumentResource;
import com.documents.domain.DocumentResourceStatus;
import com.documents.domain.DocumentResourceUsageType;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentResourceRepository;
import io.github.jho951.platform.resource.api.ResourceAccessException;
import io.github.jho951.platform.resource.api.ResourceDescriptor;
import io.github.jho951.platform.resource.api.ResourceId;
import io.github.jho951.platform.resource.api.ResourceNotFoundException;
import io.github.jho951.platform.resource.api.ResourcePrincipal;
import io.github.jho951.platform.resource.api.ResourceStatus;
import io.github.jho951.platform.resource.api.ResourceStoreRequest;
import io.github.jho951.platform.resource.api.ResourceService;
import io.github.jho951.platform.resource.spi.ResourceCatalog;
import io.github.jho951.platform.resource.spi.ResourceContentStore;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DocumentResourceBindingService {

    private static final List<DocumentResourceStatus> LIVE_STATUSES = List.of(
        DocumentResourceStatus.ACTIVE,
        DocumentResourceStatus.TRASHED
    );

    private static final List<DocumentResourceStatus> RECONCILE_STATUSES = List.of(
        DocumentResourceStatus.ACTIVE,
        DocumentResourceStatus.TRASHED,
        DocumentResourceStatus.PENDING_PURGE,
        DocumentResourceStatus.BROKEN
    );

    private static final String ATTACHMENT_KIND = "editor-attachment";
    private static final String SNAPSHOT_KIND = "document-snapshot";
    private static final String SYSTEM_ACTOR = "documents-app";

    private final DocumentResourceRepository documentResourceRepository;
    private final ResourceService resourceService;
    private final ResourceCatalog resourceCatalog;
    private final ResourceContentStore resourceContentStore;
    private final DocumentsResourcePrincipalFactory documentsResourcePrincipalFactory;
    private final JdbcTemplate jdbcTemplate;
    private final Duration purgeGracePeriod;
    private final String catalogTable;

    public DocumentResourceBindingService(
        DocumentResourceRepository documentResourceRepository,
        ResourceService resourceService,
        ResourceCatalog resourceCatalog,
        ResourceContentStore resourceContentStore,
        DocumentsResourcePrincipalFactory documentsResourcePrincipalFactory,
        JdbcTemplate jdbcTemplate,
        @Value("${documents.resource.purge-grace-period:PT24H}") Duration purgeGracePeriod,
        @Value("${platform.resource.jdbc.catalog-table:platform_resource_catalog}") String catalogTable
    ) {
        this.documentResourceRepository = documentResourceRepository;
        this.resourceService = resourceService;
        this.resourceCatalog = resourceCatalog;
        this.resourceContentStore = resourceContentStore;
        this.documentsResourcePrincipalFactory = documentsResourcePrincipalFactory;
        this.jdbcTemplate = jdbcTemplate;
        this.purgeGracePeriod = purgeGracePeriod;
        this.catalogTable = catalogTable;
    }

    @Transactional
    public void bindAttachment(Block block, String resourceId, String actorId, String ownerUserId) {
        saveBinding(DocumentResource.builder()
            .id(UUID.randomUUID())
            .documentId(block.getDocumentId())
            .blockId(block.getId())
            .resourceId(resourceId)
            .resourceKind(ATTACHMENT_KIND)
            .ownerUserId(ownerUserId)
            .usageType(DocumentResourceUsageType.BLOCK_ATTACHMENT)
            .status(DocumentResourceStatus.ACTIVE)
            .createdBy(actorId)
            .updatedBy(actorId)
            .build());
    }

    @Transactional
    public void bindSnapshot(Document document, String resourceId, String actorId, String ownerUserId) {
        saveBinding(DocumentResource.builder()
            .id(UUID.randomUUID())
            .documentId(document.getId())
            .resourceId(resourceId)
            .resourceKind(SNAPSHOT_KIND)
            .ownerUserId(ownerUserId)
            .usageType(DocumentResourceUsageType.DOCUMENT_SNAPSHOT)
            .documentVersion(document.getVersion() == null ? 0L : document.getVersion().longValue())
            .status(DocumentResourceStatus.ACTIVE)
            .createdBy(actorId)
            .updatedBy(actorId)
            .build());
    }

    @Transactional(readOnly = true)
    public DocumentResource requireActiveAttachmentBinding(UUID documentId, UUID blockId, String resourceId) {
        return documentResourceRepository.findByDocumentIdAndBlockIdAndResourceIdAndUsageTypeAndStatus(
                documentId,
                blockId,
                resourceId,
                DocumentResourceUsageType.BLOCK_ATTACHMENT,
                DocumentResourceStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.ATTACHMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public DocumentResource requireActiveSnapshotBinding(UUID documentId, String resourceId) {
        return documentResourceRepository.findByDocumentIdAndResourceIdAndUsageTypeAndStatus(
                documentId,
                resourceId,
                DocumentResourceUsageType.DOCUMENT_SNAPSHOT,
                DocumentResourceStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_SNAPSHOT_NOT_FOUND));
    }

    @Transactional
    public void trashDocumentBindings(List<UUID> documentIds, String actorId) {
        if (documentIds.isEmpty()) {
            return;
        }

        for (DocumentResource binding : documentResourceRepository.findByDocumentIdInAndStatusIn(documentIds, List.of(DocumentResourceStatus.ACTIVE))) {
            markTrashed(binding, actorId);
        }
    }

    @Transactional
    public void restoreDocumentBindings(List<UUID> documentIds, String actorId) {
        if (documentIds.isEmpty()) {
            return;
        }

        for (DocumentResource binding : documentResourceRepository.findByDocumentIdInAndStatusIn(documentIds, List.of(DocumentResourceStatus.TRASHED))) {
            binding.setStatus(DocumentResourceStatus.ACTIVE);
            binding.setUpdatedBy(normalizedActor(actorId));
            binding.setLastError(null);
        }
    }

    @Transactional
    public void scheduleDocumentBindingsForPurge(List<UUID> documentIds, String actorId) {
        if (documentIds.isEmpty()) {
            return;
        }

        ResourcePrincipal principal = actorId == null
            ? documentsResourcePrincipalFactory.systemPrincipal()
            : documentsResourcePrincipalFactory.create(actorId);

        for (DocumentResource binding : documentResourceRepository.findByDocumentIdInAndStatusIn(documentIds, LIVE_STATUSES)) {
            schedulePurge(binding, principal, actorId);
        }
    }

    @Transactional
    public void scheduleAttachmentBindingsForPurge(List<UUID> blockIds, String actorId) {
        if (blockIds.isEmpty()) {
            return;
        }

        ResourcePrincipal principal = actorId == null
            ? documentsResourcePrincipalFactory.systemPrincipal()
            : documentsResourcePrincipalFactory.create(actorId);

        for (DocumentResource binding : documentResourceRepository.findByBlockIdInAndStatusIn(blockIds, LIVE_STATUSES)) {
            if (binding.getUsageType() == DocumentResourceUsageType.BLOCK_ATTACHMENT) {
                schedulePurge(binding, principal, actorId);
            }
        }
    }

    @Transactional
    public void scheduleAttachmentPurge(UUID documentId, UUID blockId, String resourceId, String actorId) {
        DocumentResource binding = requireActiveAttachmentBinding(documentId, blockId, resourceId);
        schedulePurge(binding, documentsResourcePrincipalFactory.create(actorId), actorId);
    }

    @Transactional
    public void scheduleSnapshotPurge(UUID documentId, String resourceId, String actorId) {
        DocumentResource binding = requireActiveSnapshotBinding(documentId, resourceId);
        schedulePurge(binding, documentsResourcePrincipalFactory.create(actorId), actorId);
    }

    @Transactional
    public void purgePendingBindings() {
        LocalDateTime now = LocalDateTime.now();
        for (DocumentResource binding : documentResourceRepository.findPurgeTargets(DocumentResourceStatus.PENDING_PURGE, now)) {
            purgeBinding(binding, now);
        }
    }

    @Transactional
    public void reconcileBindings() {
        LocalDateTime now = LocalDateTime.now();

        for (DocumentResource binding : documentResourceRepository.findByStatusIn(RECONCILE_STATUSES)) {
            Optional<ResourceDescriptor> descriptor = resourceCatalog.find(new ResourceId(binding.getResourceId()));
            if (descriptor.isEmpty()) {
                if (binding.getStatus() == DocumentResourceStatus.PENDING_PURGE) {
                    markPurged(binding, now);
                    continue;
                }
                markBroken(binding, "catalog descriptor missing", now);
                continue;
            }

            if (binding.getStatus() == DocumentResourceStatus.BROKEN) {
                repairFromDescriptor(binding, descriptor.get(), now);
            }
        }

        String sql = "select id, owner_id, kind, attributes, status from " + catalogTable
            + " where kind in (?, ?)";

        jdbcTemplate.query(sql, rs -> {
            String resourceId = rs.getString("id");
            if (documentResourceRepository.existsByResourceId(resourceId)) {
                return;
            }

            Map<String, String> attributes = decodeAttributes(rs.getString("attributes"));
            Optional<DocumentResource> recreatedBinding = recreateBinding(
                resourceId,
                rs.getString("kind"),
                rs.getString("owner_id"),
                rs.getString("status"),
                attributes,
                now
            );
            recreatedBinding.ifPresent(documentResourceRepository::save);
        }, ATTACHMENT_KIND, SNAPSHOT_KIND);
    }

    private void saveBinding(DocumentResource binding) {
        documentResourceRepository.findByResourceId(binding.getResourceId()).ifPresent(existing -> binding.setId(existing.getId()));
        documentResourceRepository.save(binding);
    }

    private void schedulePurge(DocumentResource binding, ResourcePrincipal principal, String actorId) {
        try {
            resourceService.delete(principal, new ResourceId(binding.getResourceId()));
            markPendingPurge(binding, actorId, LocalDateTime.now());
        } catch (ResourceNotFoundException ex) {
            markBroken(binding, "resource descriptor missing during delete", LocalDateTime.now());
        } catch (ResourceAccessException ex) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN);
        }
    }

    private void purgeBinding(DocumentResource binding, LocalDateTime now) {
        Optional<ResourceDescriptor> descriptor = resourceCatalog.find(new ResourceId(binding.getResourceId()));
        if (descriptor.isPresent()) {
            resourceContentStore.delete(descriptor.get().storageFileId());
            resourceCatalog.delete(new ResourceId(binding.getResourceId()));
        }
        markPurged(binding, now);
    }

    private void repairFromDescriptor(DocumentResource binding, ResourceDescriptor descriptor, LocalDateTime now) {
        binding.setStatus(descriptor.status() == ResourceStatus.DELETED
            ? DocumentResourceStatus.PENDING_PURGE
            : DocumentResourceStatus.ACTIVE);
        binding.setLastError(null);
        binding.setRepairedAt(now);
        binding.setUpdatedBy(SYSTEM_ACTOR);
        if (binding.getStatus() == DocumentResourceStatus.PENDING_PURGE && binding.getPurgeAt() == null) {
            binding.setPurgeAt(now.plus(purgeGracePeriod));
            if (binding.getDeletedAt() == null) {
                binding.setDeletedAt(now);
            }
        }
    }

    private Optional<DocumentResource> recreateBinding(
        String resourceId,
        String kind,
        String ownerUserId,
        String resourceStatus,
        Map<String, String> attributes,
        LocalDateTime now
    ) {
        Optional<UUID> documentId = parseUuid(attributes.get("documentId"));
        if (documentId.isEmpty()) {
            return Optional.empty();
        }

        DocumentResourceUsageType usageType = resolveUsageType(kind);
        if (usageType == null) {
            return Optional.empty();
        }

        DocumentResourceStatus status = "DELETED".equalsIgnoreCase(resourceStatus)
            ? DocumentResourceStatus.PENDING_PURGE
            : DocumentResourceStatus.ACTIVE;

        DocumentResource binding = DocumentResource.builder()
            .id(UUID.randomUUID())
            .documentId(documentId.get())
            .blockId(parseUuid(attributes.get("blockId")).orElse(null))
            .resourceId(resourceId)
            .resourceKind(kind)
            .ownerUserId(ownerUserId)
            .usageType(usageType)
            .documentVersion(parseLong(attributes.get("documentVersion")))
            .status(status)
            .purgeAt(status == DocumentResourceStatus.PENDING_PURGE ? now.plus(purgeGracePeriod) : null)
            .deletedAt(status == DocumentResourceStatus.PENDING_PURGE ? now : null)
            .createdBy(SYSTEM_ACTOR)
            .updatedBy(SYSTEM_ACTOR)
            .repairedAt(now)
            .build();
        return Optional.of(binding);
    }

    private DocumentResourceUsageType resolveUsageType(String kind) {
        if (ATTACHMENT_KIND.equals(kind)) {
            return DocumentResourceUsageType.BLOCK_ATTACHMENT;
        }
        if (SNAPSHOT_KIND.equals(kind)) {
            return DocumentResourceUsageType.DOCUMENT_SNAPSHOT;
        }
        return null;
    }

    private void markTrashed(DocumentResource binding, String actorId) {
        binding.setStatus(DocumentResourceStatus.TRASHED);
        binding.setUpdatedBy(normalizedActor(actorId));
        binding.setLastError(null);
    }

    private void markPendingPurge(DocumentResource binding, String actorId, LocalDateTime now) {
        binding.setStatus(DocumentResourceStatus.PENDING_PURGE);
        binding.setDeletedAt(now);
        binding.setPurgeAt(now.plus(purgeGracePeriod));
        binding.setUpdatedBy(normalizedActor(actorId));
        binding.setLastError(null);
    }

    private void markPurged(DocumentResource binding, LocalDateTime now) {
        binding.setStatus(DocumentResourceStatus.PURGED);
        if (binding.getDeletedAt() == null) {
            binding.setDeletedAt(now);
        }
        binding.setPurgeAt(now);
        binding.setUpdatedBy(SYSTEM_ACTOR);
        binding.setLastError(null);
    }

    private void markBroken(DocumentResource binding, String message, LocalDateTime now) {
        binding.setStatus(DocumentResourceStatus.BROKEN);
        binding.setLastError(message);
        binding.setUpdatedBy(SYSTEM_ACTOR);
        binding.setRepairedAt(null);
        if (binding.getDeletedAt() == null && binding.getStatus() == DocumentResourceStatus.PENDING_PURGE) {
            binding.setDeletedAt(now);
        }
    }

    private Map<String, String> decodeAttributes(String encodedAttributes) {
        if (!StringUtils.hasText(encodedAttributes)) {
            return Map.of();
        }

        try {
            Properties properties = new Properties();
            properties.load(new StringReader(encodedAttributes));
            return properties.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    entry -> entry.getKey().toString(),
                    entry -> entry.getValue().toString()
                ));
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private Optional<UUID> parseUuid(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(rawValue));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Long parseLong(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizedActor(String actorId) {
        return actorId == null ? SYSTEM_ACTOR : actorId;
    }
}
