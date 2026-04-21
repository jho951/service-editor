package com.documents.repository;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.documents.domain.DocumentResource;
import com.documents.domain.DocumentResourceStatus;
import com.documents.domain.DocumentResourceUsageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentResourceRepository extends JpaRepository<DocumentResource, UUID> {

    @Query("""
        select dr
        from DocumentResource dr
        where dr.documentId = :documentId
          and dr.resourceId = :resourceId
          and dr.usageType = :usageType
          and dr.status = :status
        """)
    Optional<DocumentResource> findByDocumentIdAndResourceIdAndUsageTypeAndStatus(
        @Param("documentId") UUID documentId,
        @Param("resourceId") String resourceId,
        @Param("usageType") DocumentResourceUsageType usageType,
        @Param("status") DocumentResourceStatus status
    );

    @Query("""
        select dr
        from DocumentResource dr
        where dr.documentId = :documentId
          and dr.blockId = :blockId
          and dr.resourceId = :resourceId
          and dr.usageType = :usageType
          and dr.status = :status
        """)
    Optional<DocumentResource> findByDocumentIdAndBlockIdAndResourceIdAndUsageTypeAndStatus(
        @Param("documentId") UUID documentId,
        @Param("blockId") UUID blockId,
        @Param("resourceId") String resourceId,
        @Param("usageType") DocumentResourceUsageType usageType,
        @Param("status") DocumentResourceStatus status
    );

    @Query("""
        select dr
        from DocumentResource dr
        where dr.documentId in :documentIds
          and dr.status in :statuses
        """)
    List<DocumentResource> findByDocumentIdInAndStatusIn(
        @Param("documentIds") List<UUID> documentIds,
        @Param("statuses") List<DocumentResourceStatus> statuses
    );

    @Query("""
        select dr
        from DocumentResource dr
        where dr.blockId in :blockIds
          and dr.status in :statuses
        """)
    List<DocumentResource> findByBlockIdInAndStatusIn(
        @Param("blockIds") List<UUID> blockIds,
        @Param("statuses") List<DocumentResourceStatus> statuses
    );

    Optional<DocumentResource> findByResourceId(String resourceId);

    boolean existsByResourceId(String resourceId);

    @Query("""
        select dr
        from DocumentResource dr
        where dr.status = :status
          and dr.purgeAt is not null
          and dr.purgeAt <= :threshold
        """)
    List<DocumentResource> findPurgeTargets(
        @Param("status") DocumentResourceStatus status,
        @Param("threshold") LocalDateTime threshold
    );

    List<DocumentResource> findByStatusIn(List<DocumentResourceStatus> statuses);
}
