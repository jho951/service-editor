package com.documents.repository;

import com.documents.domain.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select max(d.sortKey)
            from Document d
            where d.workspace.id = :workspaceId
              and (
                (:parentId is null and d.parent is null)
                or d.parent.id = :parentId
              )
              and d.deletedAt is null
            """)
    Optional<String> findMaxSortKeyByWorkspaceIdAndParentId(
            @Param("workspaceId") UUID workspaceId,
            @Param("parentId") UUID parentId
    );

    @Query("""
            select d
            from Document d
            where d.workspace.id = :workspaceId
              and d.deletedAt is null
            order by
              d.sortKey asc,
              d.createdAt asc,
              d.id asc
            """)
    List<Document> findActiveByWorkspaceIdOrderBySortKey(@Param("workspaceId") UUID workspaceId);
}
