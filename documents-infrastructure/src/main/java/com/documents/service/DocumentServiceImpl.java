package com.documents.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Document;
import com.documents.domain.DocumentVisibility;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.support.OrderedSortKeyGenerator;
import com.documents.support.OrderedSortKeyGenerator.SortKeyRebalanceRequiredException;
import com.documents.support.TextNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

	private final BlockService blockService;
	private final DocumentRepository documentRepository;
	private final WorkspaceService workspaceService;
	private final TextNormalizer textNormalizer;
	private final OrderedSortKeyGenerator orderedSortKeyGenerator;

	@Override
	@Transactional
	public Document create(UUID workspaceId, UUID parentId, String title, String iconJson, String coverJson,
		String actorId) {
		Workspace workspace = workspaceService.getById(workspaceId);
		Document parentDocument = validateParentForWorkspace(workspaceId, parentId);

		String normalizedActorId = textNormalizer.normalizeNullable(actorId);
		String normalizedTitle = textNormalizer.normalizeRequired(title);
		List<Document> siblings = documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(workspaceId, parentId);
		String nextSortKey = generateSortKey(siblings, null, null);

		Document document = Document.builder()
			.id(UUID.randomUUID())
			.workspace(workspace)
			.parent(parentDocument)
			.title(normalizedTitle)
			.iconJson(iconJson)
			.coverJson(coverJson)
			.visibility(DocumentVisibility.PRIVATE)
			.sortKey(nextSortKey)
			.createdBy(normalizedActorId)
			.updatedBy(normalizedActorId)
			.build();

		return documentRepository.save(document);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Document> getAllByWorkspaceId(UUID workspaceId) {
		workspaceService.getById(workspaceId);
		return documentRepository.findActiveByWorkspaceIdOrderBySortKey(workspaceId);
	}

	@Override
	@Transactional(readOnly = true)
	public Document getById(UUID documentId) {
		return documentRepository.findByIdAndDeletedAtIsNull(documentId)
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
	}

	@Override
	@Transactional
	public Document update(UUID documentId, String title, String iconJson, String coverJson, UUID parentId,
		Integer version, String actorId) {
		Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		if (!Objects.equals(document.getVersion(), version)) {
			throw new BusinessException(BusinessErrorCode.CONFLICT);
		}

		Document parentDocument = findValidParentForUpdate(document, documentId, parentId);

		String nextTitle = title == null ? document.getTitle() : textNormalizer.normalizeRequired(title);
		String nextIconJson = normalizeNullableMetaJson(iconJson);
		String nextCoverJson = normalizeNullableMetaJson(coverJson);

		if (Objects.equals(document.getTitle(), nextTitle)
			&& Objects.equals(document.getIconJson(), nextIconJson)
			&& Objects.equals(document.getCoverJson(), nextCoverJson)
			&& Objects.equals(document.getParentId(), parentId)) {
			return document;
		}

		document.setTitle(nextTitle);
		document.setIconJson(nextIconJson);
		document.setCoverJson(nextCoverJson);
		document.setParent(parentDocument);
		document.setUpdatedBy(textNormalizer.normalizeNullable(actorId));

		return document;
	}

	@Override
	@Transactional
	public Document updateVisibility(UUID documentId, DocumentVisibility visibility, Integer version, String actorId) {
		Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		if (!Objects.equals(document.getVersion(), version)) {
			throw new BusinessException(BusinessErrorCode.CONFLICT);
		}

		if (document.getVisibility() == visibility) {
			return document;
		}

		document.setVisibility(visibility);
		document.setUpdatedBy(textNormalizer.normalizeNullable(actorId));
		return document;
	}

	@Override
	@Transactional
	public void delete(UUID documentId, String actorId) {
		String normalizedActorId = textNormalizer.normalizeNullable(actorId);
		LocalDateTime deletedAt = LocalDateTime.now();
		List<UUID> documentIdsToDelete = collectActiveDocumentTreeIds(findActiveDocument(documentId));

		documentRepository.softDeleteActiveByIds(documentIdsToDelete, normalizedActorId, deletedAt);

		for (UUID currentDocumentId : documentIdsToDelete) {
			DocumentVersionIncrementContext.runWithoutIncrement(() -> {
				blockService.softDeleteAllByDocumentId(currentDocumentId, normalizedActorId, deletedAt);
				return null;
			});
		}
	}

	@Override
	@Transactional
	public void restore(UUID documentId, String actorId) {
		Document deletedDocument = findDeletedDocument(documentId);
		validateParentForRestore(deletedDocument);

		String normalizedActorId = textNormalizer.normalizeNullable(actorId);
		LocalDateTime restoredAt = LocalDateTime.now();
		List<UUID> documentIdsToRestore = collectDeletedDocumentTreeIds(deletedDocument);

		documentRepository.restoreDeletedByIds(documentIdsToRestore, normalizedActorId, restoredAt);

		for (UUID currentDocumentId : documentIdsToRestore) {
			DocumentVersionIncrementContext.runWithoutIncrement(() -> {
				blockService.restoreAllByDocumentId(currentDocumentId, normalizedActorId, restoredAt);
				return null;
			});
		}
	}

	@Override
	@Transactional
	public void move(UUID documentId, UUID targetParentId, UUID afterDocumentId, UUID beforeDocumentId,
		String actorId) {
		Document document = findActiveDocument(documentId);
		Document targetParentDocument = findValidParentForMove(document, targetParentId);

		List<Document> siblings = documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(
			document.getWorkspaceId(),
			targetParentId
		);

		List<Document> targetSiblings = siblings.stream()
			.filter(sibling -> !documentId.equals(sibling.getId()))
			.toList();

		String nextSortKey = generateSortKey(targetSiblings, afterDocumentId, beforeDocumentId);

		if (Objects.equals(document.getParentId(), targetParentId)
			&& Objects.equals(document.getSortKey(), nextSortKey)) {
			return;
		}

		document.setParent(targetParentDocument);
		document.setSortKey(nextSortKey);
		document.setUpdatedBy(textNormalizer.normalizeNullable(actorId));
	}

	private String generateSortKey(List<Document> siblings, UUID afterDocumentId, UUID beforeDocumentId) {
		try {
			return orderedSortKeyGenerator.generate(
				siblings,
				Document::getId,
				Document::getSortKey,
				afterDocumentId,
				beforeDocumentId
			);
		} catch (SortKeyRebalanceRequiredException ex) {
			throw new BusinessException(BusinessErrorCode.SORT_KEY_REBALANCE_REQUIRED);
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}
	}

	private Document validateParentForWorkspace(UUID workspaceId, UUID parentId) {
		if (parentId == null) {
			return null;
		}

		Document parentDocument = findActiveDocument(parentId);
		if (!workspaceId.equals(parentDocument.getWorkspaceId())) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}

		return parentDocument;
	}

	private Document findValidParentForUpdate(Document document, UUID documentId, UUID parentId) {
		if (Objects.equals(documentId, parentId)) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}

		if (parentId == null) {
			return null;
		}

		Document parentDocument = findActiveDocument(parentId);

		if (!document.getWorkspaceId().equals(parentDocument.getWorkspaceId())) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}

		validateNoCycle(documentId, parentDocument);
		return parentDocument;
	}

	private Document findValidParentForMove(Document document, UUID targetParentId) {
		if (Objects.equals(document.getId(), targetParentId)) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}

		if (targetParentId == null) {
			return null;
		}

		Document parentDocument = findActiveDocument(targetParentId);

		if (!document.getWorkspaceId().equals(parentDocument.getWorkspaceId())) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}

		validateNoCycle(document.getId(), parentDocument);
		return parentDocument;
	}

	private void validateNoCycle(UUID documentId, Document parentDocument) {
		Document current = parentDocument;

		while (current != null) {
			if (documentId.equals(current.getId())) {
				throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
			}

			current = current.getParentId() == null ? null : findActiveDocument(current.getParentId());
		}
	}

	private void validateParentForRestore(Document document) {
		if (document.getParentId() == null) {
			return;
		}

		Document parentDocument = documentRepository.findById(document.getParentId())
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.INVALID_REQUEST));

		if (parentDocument.getDeletedAt() != null) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}
	}

	private Document findActiveDocument(UUID documentId) {
		return documentRepository.findByIdAndDeletedAtIsNull(documentId)
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
	}

	private Document findDeletedDocument(UUID documentId) {
		Document document = documentRepository.findById(documentId)
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		if (document.getDeletedAt() == null) {
			throw new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND);
		}

		return document;
	}

	private List<UUID> collectActiveDocumentTreeIds(Document rootDocument) {
		List<UUID> documentIds = new ArrayList<>();
		documentIds.add(rootDocument.getId());

		List<Document> children = documentRepository.findActiveChildrenByParentIdOrderBySortKey(rootDocument.getId());
		for (Document child : children) {
			documentIds.addAll(collectActiveDocumentTreeIds(child));
		}

		return documentIds;
	}

	private List<UUID> collectDeletedDocumentTreeIds(Document rootDocument) {
		List<UUID> documentIds = new ArrayList<>();
		documentIds.add(rootDocument.getId());

		List<Document> children = documentRepository.findDeletedChildrenByParentIdOrderBySortKey(rootDocument.getId());
		for (Document child : children) {
			documentIds.addAll(collectDeletedDocumentTreeIds(child));
		}

		return documentIds;
	}

	private String normalizeNullableMetaJson(String value) {
		String normalizedValue = textNormalizer.normalizeNullable(value);
		return "null".equals(normalizedValue) ? null : normalizedValue;
	}
}
