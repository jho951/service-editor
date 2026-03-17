package com.documents.repository;

import com.documents.domain.Block;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    Optional<Block> findByIdAndDeletedAtIsNull(UUID id);

    long countByDocumentIdAndDeletedAtIsNull(UUID documentId);

    @Query("""
            select b
            from Block b
            where b.documentId = :documentId
              and (
                (:parentId is null and b.parentId is null)
                or b.parentId = :parentId
              )
              and b.deletedAt is null
            order by
              b.sortKey asc,
              b.createdAt asc,
              b.id asc
            """)
    List<Block> findActiveByDocumentIdAndParentIdOrderBySortKey(
            @Param("documentId") UUID documentId,
            @Param("parentId") UUID parentId
    );
}
