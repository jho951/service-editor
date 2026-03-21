package com.documents.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.documents.domain.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

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

	@Query("""
		select d
		from Document d
		where d.workspace.id = :workspaceId
		  and (
		    (:parentId is null and d.parent is null)
		    or d.parent.id = :parentId
		  )
		  and d.deletedAt is null
		order by
		  d.sortKey asc,
		  d.createdAt asc,
		  d.id asc
		""")
	List<Document> findActiveByWorkspaceIdAndParentIdOrderBySortKey(
		@Param("workspaceId") UUID workspaceId,
		@Param("parentId") UUID parentId
	);

	@Query("""
		select d
		from Document d
		where d.parent.id = :parentId
		  and d.deletedAt is null
		order by
		  d.sortKey asc,
		  d.createdAt asc,
		  d.id asc
		""")
	List<Document> findActiveChildrenByParentIdOrderBySortKey(@Param("parentId") UUID parentId);

	@Query("""
		select d
		from Document d
		where d.parent.id = :parentId
		  and d.deletedAt is not null
		order by
		  d.sortKey asc,
		  d.createdAt asc,
		  d.id asc
		""")
	List<Document> findDeletedChildrenByParentIdOrderBySortKey(@Param("parentId") UUID parentId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update Document d
		set d.deletedAt = :deletedAt,
		    d.updatedBy = :actorId
		where d.id in :documentIds
		  and d.deletedAt is null
		""")
	int softDeleteActiveByIds(
		@Param("documentIds") List<UUID> documentIds,
		@Param("actorId") String actorId,
		@Param("deletedAt") LocalDateTime deletedAt
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update Document d
		set d.deletedAt = null,
		    d.updatedBy = :actorId,
		    d.updatedAt = :updatedAt
		where d.id in :documentIds
		  and d.deletedAt is not null
		""")
	int restoreDeletedByIds(
		@Param("documentIds") List<UUID> documentIds,
		@Param("actorId") String actorId,
		@Param("updatedAt") LocalDateTime updatedAt
	);
}
