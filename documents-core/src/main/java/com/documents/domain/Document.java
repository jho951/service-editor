package com.documents.domain;

import com.documents.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "documents")
public class Document extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "char(36)")
    private UUID id;

    @Column(name = "workspace_id", nullable = false, columnDefinition = "char(36)")
    private UUID workspaceId;

    @Column(name = "parent_id", columnDefinition = "char(36)")
    private UUID parentId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "icon_json", columnDefinition = "longtext")
    private String iconJson;

    @Column(name = "cover_json", columnDefinition = "longtext")
    private String coverJson;

    @Column(name = "sort_key", length = 255)
    private String sortKey;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
