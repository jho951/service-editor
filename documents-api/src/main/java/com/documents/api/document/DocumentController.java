package com.documents.api.document;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documents.api.auth.CurrentUserId;
import com.documents.api.block.BlockApiMapper;
import com.documents.api.block.dto.BlockResponse;
import com.documents.api.code.SuccessCode;
import com.documents.api.document.dto.CreateDocumentRequest;
import com.documents.api.document.dto.DocumentResponse;
import com.documents.api.document.dto.MoveDocumentRequest;
import com.documents.api.document.dto.DocumentTransactionRequest;
import com.documents.api.document.dto.DocumentTransactionResponse;
import com.documents.api.document.dto.TrashDocumentResponse;
import com.documents.api.document.dto.UpdateDocumentRequest;
import com.documents.api.document.dto.UpdateDocumentVisibilityRequest;
import com.documents.api.dto.GlobalResponse;
import com.documents.domain.Document;
import com.documents.service.BlockService;
import com.documents.service.DocumentService;
import com.documents.service.DocumentTransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Document", description = "문서 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/documents")
public class DocumentController {

	private final BlockService blockService;
	private final BlockApiMapper blockApiMapper;
	private final DocumentService documentService;
	private final DocumentApiMapper documentApiMapper;
	private final DocumentTransactionService documentTransactionService;
	private final DocumentTransactionApiMapper documentTransactionApiMapper;

	@Operation(summary = "내 문서 목록 조회")
	@GetMapping
	public ResponseEntity<GlobalResponse<List<DocumentResponse>>> getDocuments(
		@CurrentUserId String userId
	) {
		List<DocumentResponse> response = documentService.getAllByUserId(userId).stream()
			.map(documentApiMapper::toResponse)
			.toList();
		return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, response));
	}

	@Operation(summary = "내 휴지통 문서 목록 조회")
	@GetMapping("/trash")
	public ResponseEntity<GlobalResponse<List<TrashDocumentResponse>>> getTrashDocuments(
		@CurrentUserId String userId
	) {
		List<TrashDocumentResponse> response = documentService.getTrashByUserId(userId).stream()
			.map(documentApiMapper::toTrashResponse)
			.toList();
		return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, response));
	}

	@Operation(summary = "문서 생성")
	@PostMapping
	public ResponseEntity<GlobalResponse<DocumentResponse>> createDocument(
		@Valid @RequestBody CreateDocumentRequest request,
		@CurrentUserId String userId
	) {
		Document createdDocument = documentService.create(
			request.getParentId(),
			request.getTitle(),
			documentApiMapper.serializeIcon(request),
			documentApiMapper.serializeCover(request),
			userId
		);

		return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
			.body(GlobalResponse.ok(SuccessCode.CREATED, documentApiMapper.toResponse(createdDocument)));
	}

	@Operation(summary = "문서 단건 조회")
	@GetMapping("/{documentId}")
	public ResponseEntity<GlobalResponse<DocumentResponse>> getDocument(
		@PathVariable("documentId") UUID documentId
	) {
		Document document = documentService.getById(documentId);
		return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, documentApiMapper.toResponse(document)));
	}

	@Operation(summary = "문서 블록 목록 조회")
	@GetMapping("/{documentId}/blocks")
	public ResponseEntity<GlobalResponse<List<BlockResponse>>> getBlocks(
		@PathVariable("documentId") UUID documentId
	) {
		List<BlockResponse> response = blockService.getAllByDocumentId(documentId).stream()
			.map(blockApiMapper::toResponse)
			.toList();
		return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, response));
	}

	@Operation(summary = "문서 수정")
	@PatchMapping("/{documentId}")
	public ResponseEntity<GlobalResponse<DocumentResponse>> updateDocument(
		@PathVariable("documentId") UUID documentId,
		@Valid @RequestBody UpdateDocumentRequest request,
		@CurrentUserId String userId
	) {
		Document updatedDocument = documentService.update(
			documentId,
			request.getTitle(),
			documentApiMapper.serializeIcon(request),
			documentApiMapper.serializeCover(request),
			request.getVersion(),
			userId
		);
		return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, documentApiMapper.toResponse(updatedDocument)));
	}

	@Operation(summary = "문서 공개 상태 수정")
	@PatchMapping("/{documentId}/visibility")
	public ResponseEntity<GlobalResponse<DocumentResponse>> updateDocumentVisibility(
		@PathVariable("documentId") UUID documentId,
		@Valid @RequestBody UpdateDocumentVisibilityRequest request,
		@CurrentUserId String userId
	) {
		Document updatedDocument = documentService.updateVisibility(
			documentId,
			request.getVisibility(),
			request.getVersion(),
			userId
		);
		return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, documentApiMapper.toResponse(updatedDocument)));
	}

	@Operation(summary = "문서 에디터 transaction 반영")
	@PostMapping("/{documentId}/transactions")
	public ResponseEntity<GlobalResponse<DocumentTransactionResponse>> applyTransactions(
		@PathVariable("documentId") UUID documentId,
		@Valid @RequestBody DocumentTransactionRequest request,
		@CurrentUserId String userId
	) {
		DocumentTransactionResponse response = documentTransactionApiMapper.toResponse(
			documentTransactionService.apply(documentId, documentTransactionApiMapper.toCommand(request), userId)
		);
		return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, response));
	}

	@Operation(summary = "문서 삭제")
	@DeleteMapping("/{documentId}")
	public ResponseEntity<GlobalResponse<Void>> deleteDocument(
		@PathVariable("documentId") UUID documentId,
		@CurrentUserId String userId
	) {
		documentService.delete(documentId, userId);
		return ResponseEntity.ok(GlobalResponse.ok());
	}

	@Operation(summary = "문서 휴지통 이동")
	@PatchMapping("/{documentId}/trash")
	public ResponseEntity<GlobalResponse<Void>> trashDocument(
		@PathVariable("documentId") UUID documentId,
		@CurrentUserId String userId
	) {
		documentService.trash(documentId, userId);
		return ResponseEntity.ok(GlobalResponse.ok());
	}

	@Operation(summary = "문서 복구")
	@PostMapping("/{documentId}/restore")
	public ResponseEntity<GlobalResponse<Void>> restoreDocument(
		@PathVariable("documentId") UUID documentId,
		@CurrentUserId String userId
	) {
		documentService.restore(documentId, userId);
		return ResponseEntity.ok(GlobalResponse.ok());
	}

	@Operation(summary = "문서 이동")
	@PostMapping("/{documentId}/move")
	public ResponseEntity<GlobalResponse<Void>> moveDocument(
		@PathVariable("documentId") UUID documentId,
		@RequestBody MoveDocumentRequest request,
		@CurrentUserId String userId
	) {
		documentService.move(
			documentId,
			request.getTargetParentId(),
			request.getAfterDocumentId(),
			request.getBeforeDocumentId(),
			userId
		);
		return ResponseEntity.ok(GlobalResponse.ok());
	}
}
