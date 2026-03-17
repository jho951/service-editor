package com.documents.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documents.domain.Document;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.repository.DocumentRepository;
import com.documents.support.DocumentSortKeyGenerator;
import com.documents.support.TextNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

	private final DocumentRepository documentRepository;
	private final WorkspaceService workspaceService;
	private final TextNormalizer textNormalizer;
	private final DocumentSortKeyGenerator documentSortKeyGenerator;

	@Override
	@Transactional
	public Document create(UUID workspaceId, UUID parentId, String title, String iconJson, String coverJson,
		String actorId) {
		workspaceService.getById(workspaceId);

		if (parentId != null) {
			Document parentDocument = documentRepository.findByIdAndDeletedAtIsNull(parentId)
				.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

			if (!workspaceId.equals(parentDocument.getWorkspaceId())) {
				throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
			}
		}

		String normalizedActorId = textNormalizer.normalizeNullable(actorId);
		String normalizedTitle = textNormalizer.normalizeRequired(title);
		String nextSortKey = documentSortKeyGenerator.genNextSortKey(workspaceId, parentId);

		Document document = Document.builder()
			.id(UUID.randomUUID())
			.workspaceId(workspaceId)
			.parentId(parentId)
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

		validateParentForUpdate(document, documentId, parentId);
		applyTitle(document, title);
		applyMetadata(document, iconJson, coverJson);
		document.setParentId(parentId);
		document.setUpdatedBy(textNormalizer.normalizeNullable(actorId));

		return document;
	}

	private void validateParentForUpdate(Document document, UUID documentId, UUID parentId) {
		if (Objects.equals(documentId, parentId)) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}

		if (parentId == null) {
			return;
		}

		Document parentDocument = documentRepository.findByIdAndDeletedAtIsNull(parentId)
			.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));

		if (!document.getWorkspaceId().equals(parentDocument.getWorkspaceId())) {
			throw new BusinessException(BusinessErrorCode.INVALID_REQUEST);
		}

		validateNoCycle(documentId, parentDocument);
	}

	private void applyTitle(Document document, String title) {
		if (title == null) {
			return;
		}

		String normalizedTitle = textNormalizer.normalizeRequired(title);
		if (normalizedTitle.isEmpty()) {
			throw new BusinessException(BusinessErrorCode.VALIDATION_ERROR);
		}

		document.setTitle(normalizedTitle);
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

			UUID nextParentId = current.getParentId();
			if (nextParentId == null) {
				return;
			}

			current = documentRepository.findByIdAndDeletedAtIsNull(nextParentId)
				.orElseThrow(() -> new BusinessException(BusinessErrorCode.DOCUMENT_NOT_FOUND));
		}
	}

	private String normalizeNullableMetaJson(String value) {
		String normalizedValue = textNormalizer.normalizeNullable(value);
		return "null".equals(normalizedValue) ? null : normalizedValue;
	}
}
