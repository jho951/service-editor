package com.documents.domain;

import com.documents.common.BaseAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
@Entity
@Table(name = "blocks")
public class Block extends BaseAuditableEntity {

    @Id
    @Column(name = "block_id", nullable = false, updatable = false, columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "document_id",
            nullable = false,
            columnDefinition = "char(36)",
            foreignKey = @ForeignKey(name = "fk_blocks_document")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_id",
            columnDefinition = "char(36)",
            foreignKey = @ForeignKey(name = "fk_blocks_parent")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private Block parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE)
    @Default
    @ToString.Exclude
    private List<Block> children = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private BlockType type;

    @Column(name = "content_json", nullable = false, columnDefinition = "longtext")
    private String content;

    @Column(name = "sort_key", nullable = false, length = 24)
    private String sortKey;

    public UUID getDocumentId() {
        return document == null ? null : document.getId();
    }

    public UUID getParentId() {
        return parent == null ? null : parent.getId();
    }
}
