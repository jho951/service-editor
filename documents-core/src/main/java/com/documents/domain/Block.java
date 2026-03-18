package com.documents.domain;

import com.documents.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@Entity
@Table(name = "blocks")
public class Block extends BaseEntity {

    @Id
    @Column(name = "block_id", nullable = false, updatable = false, columnDefinition = "char(36)")
    private UUID id;

    @Column(name = "document_id", nullable = false, columnDefinition = "char(36)")
    private UUID documentId;

    @Column(name = "parent_id", columnDefinition = "char(36)")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private BlockType type;

    @Column(name = "text", nullable = false, length = 10000)
    private String text;

    @Column(name = "sort_key", nullable = false, length = 24)
    private String sortKey;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
