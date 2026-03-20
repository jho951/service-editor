package com.documents.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.support.DocumentSortKeyGenerator;
import com.documents.support.TextNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

	private final BlockService blockService;
	private final DocumentRepository documentRepository;
	private final WorkspaceService workspaceService;
	private final TextNormalizer textNormalizer;
	private final DocumentSortKeyGenerator documentSortKeyGenerator;

	@Override
	@Transactional
	public Document create(UUID workspaceId, UUID parentId, String title, String iconJson, String coverJson,
		String actorId) {
		Workspace workspace = workspaceService.getById(workspaceId);
		Document parentDocument = validateParentForWorkspace(workspaceId, parentId);

		String normalizedActorId = textNormalizer.normalizeNullable(actorId);
		String normalizedTitle = textNormalizer.normalizeRequired(title);
		String nextSortKey = documentSortKeyGenerator.genNextSortKey(workspaceId, parentId);

		Document document = Document.builder()
			.id(UUID.randomUUID())
			.workspace(workspace)
			.parent(parentDocument)
			.title(normalizedTitle)
			.iconJson(iconJson)
			.coverJson(coverJson)
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
		String actorId) {
		Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		Document parentDocument = findValidParentForUpdate(document, documentId, parentId);
		applyTitle(document, title);
		applyMetadata(document, iconJson, coverJson);
		document.setParent(parentDocument);
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
			blockService.softDeleteAllByDocumentId(currentDocumentId, normalizedActorId, deletedAt);
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
			blockService.restoreAllByDocumentId(currentDocumentId, normalizedActorId, restoredAt);
		}
	}

	@Override
	@Transactional
	public void move(UUID documentId, UUID targetParentId, UUID afterDocumentId, UUID beforeDocumentId,
		String actorId) {
		Document document = findActiveDocument(documentId);
		findValidParentForMove(document, targetParentId);

		List<Document> siblings = documentRepository.findActiveByWorkspaceIdAndParentIdOrderBySortKey(
			document.getWorkspaceId(),
			targetParentId
		);

		try {
			documentSortKeyGenerator.generate(siblings, documentId, afterDocumentId, beforeDocumentId);
		} catch (DocumentSortKeyGenerator.SortKeyRebalanceRequiredException ex) {
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

	private void applyTitle(Document document, String title) {
		if (title == null) {
			return;
		}

		document.setTitle(textNormalizer.normalizeRequired(title));
	}

	private void applyMetadata(Document document, String iconJson, String coverJson) {
		document.setIconJson(normalizeNullableMetaJson(iconJson));
		document.setCoverJson(normalizeNullableMetaJson(coverJson));
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
