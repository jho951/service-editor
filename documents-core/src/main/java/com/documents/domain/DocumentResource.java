package com.documents.domain;

import com.documents.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@Entity
@Table(name = "document_resources")
public class DocumentResource extends BaseAuditableEntity {

    @Id
    @Column(name = "document_resource_id", nullable = false, updatable = false, columnDefinition = "char(36)")
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false, columnDefinition = "char(36)")
    private UUID documentId;

    @Column(name = "block_id", columnDefinition = "char(36)")
    private UUID blockId;

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId;

    @Column(name = "resource_kind", nullable = false, length = 64)
    private String resourceKind;

    @Column(name = "owner_user_id", nullable = false, length = 64)
    private String ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 64)
    private DocumentResourceUsageType usageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DocumentResourceStatus status;

    @Column(name = "document_version")
    private Long documentVersion;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "purge_at")
    private LocalDateTime purgeAt;

    @Column(name = "last_error", length = 2048)
    private String lastError;

    @Column(name = "repaired_at")
    private LocalDateTime repairedAt;
}
