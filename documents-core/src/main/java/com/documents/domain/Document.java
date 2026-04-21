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
@Table(name = "documents")
public class Document extends BaseAuditableEntity {

    @Id
    @Column(name = "document_id", nullable = false, updatable = false, columnDefinition = "char(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_id",
            columnDefinition = "char(36)",
            foreignKey = @ForeignKey(name = "fk_documents_parent")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private Document parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE)
    @Default
    @ToString.Exclude
    private List<Document> children = new ArrayList<>();

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "icon_json", columnDefinition = "longtext")
    private String iconJson;

    @Column(name = "cover_json", columnDefinition = "longtext")
    private String coverJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    @Default
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;

    @Column(name = "sort_key", nullable = false, length = 255)
    private String sortKey;

    public UUID getParentId() {
        return parent == null ? null : parent.getId();
    }
}
