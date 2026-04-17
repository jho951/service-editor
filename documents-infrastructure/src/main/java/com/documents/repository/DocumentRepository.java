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
		where d.createdBy = :userId
		  and d.deletedAt is null
		order by
		  d.sortKey asc,
		  d.createdAt asc,
		  d.id asc
		""")
	List<Document> findActiveByCreatedByOrderBySortKey(@Param("userId") String userId);

	@Query("""
		select d
		from Document d
		where d.createdBy = :userId
		  and d.deletedAt is not null
		order by
		  d.deletedAt desc,
		  d.createdAt asc,
		  d.id asc
		""")
	List<Document> findDeletedByCreatedByOrderByDeletedAtDesc(@Param("userId") String userId);

	@Query("""
		select d
		from Document d
		where d.createdBy = :userId
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
	List<Document> findActiveByCreatedByAndParentIdOrderBySortKey(
		@Param("userId") String userId,
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

	@Query("""
		select d
		from Document d
		left join d.parent p
		where d.deletedAt is not null
		  and d.deletedAt <= :expiredAt
		  and (
		    p is null
		    or p.deletedAt is null
		  )
		order by
		  d.deletedAt asc,
		  d.createdAt asc,
		  d.id asc
		""")
	List<Document> findExpiredTrashRoots(@Param("expiredAt") LocalDateTime expiredAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update Document d
		set d.deletedAt = :deletedAt,
		    d.updatedAt = :deletedAt,
		    d.updatedBy = :actorId,
		    d.version = d.version + 1
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
		    d.updatedAt = :updatedAt,
		    d.version = d.version + 1
		where d.id in :documentIds
		  and d.deletedAt is not null
		""")
	int restoreDeletedByIds(
		@Param("documentIds") List<UUID> documentIds,
		@Param("actorId") String actorId,
		@Param("updatedAt") LocalDateTime updatedAt
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update Document d
		set d.version = d.version + 1,
		    d.updatedBy = :actorId,
		    d.updatedAt = :updatedAt
		where d.id = :documentId
		  and d.deletedAt is null
		""")
	int incrementVersion(
		@Param("documentId") UUID documentId,
		@Param("actorId") String actorId,
		@Param("updatedAt") LocalDateTime updatedAt
	);
}
