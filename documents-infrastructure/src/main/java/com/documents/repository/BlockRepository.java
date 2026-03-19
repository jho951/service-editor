package com.documents.repository;

import com.documents.domain.Block;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    Optional<Block> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select count(b)
            from Block b
            where b.document.id = :documentId
              and b.deletedAt is null
            """)
    long countActiveByDocumentId(@Param("documentId") UUID documentId);

    @Query("""
            select b
            from Block b
            where b.document.id = :documentId
              and b.deletedAt is null
            order by
              b.sortKey asc,
              b.createdAt asc,
              b.id asc
            """)
    List<Block> findActiveByDocumentIdOrderBySortKey(
            @Param("documentId") UUID documentId
    );

    @Query("""
            select b
            from Block b
            where b.document.id = :documentId
              and (
                (:parentId is null and b.parent is null)
                or b.parent.id = :parentId
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Block b
            set b.deletedAt = :deletedAt,
                b.updatedBy = :actorId
            where b.document.id = :documentId
              and b.deletedAt is null
            """)
    void softDeleteActiveByDocumentId(
            @Param("documentId") UUID documentId,
            @Param("actorId") String actorId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
