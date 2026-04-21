package com.documents.api.document;

import java.util.UUID;

import com.documents.api.auth.CurrentUserId;
import com.documents.api.code.SuccessCode;
import com.documents.api.document.dto.DocumentSnapshotResponse;
import com.documents.api.dto.GlobalResponse;
import com.documents.service.DocumentSnapshotService;
import com.documents.service.snapshot.DocumentSnapshotContent;
import com.documents.service.snapshot.DocumentSnapshotDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DocumentSnapshot", description = "문서 스냅샷 API")
@RestController
@RequestMapping("/documents")
public class DocumentSnapshotController {

    private final DocumentSnapshotService documentSnapshotService;

    public DocumentSnapshotController(DocumentSnapshotService documentSnapshotService) {
        this.documentSnapshotService = documentSnapshotService;
    }

    @Operation(summary = "문서 스냅샷 생성")
    @PostMapping("/{documentId}/snapshots")
    public ResponseEntity<GlobalResponse<DocumentSnapshotResponse>> createSnapshot(
        @PathVariable("documentId") UUID documentId,
        @CurrentUserId String userId
    ) {
        DocumentSnapshotDescriptor descriptor = documentSnapshotService.create(documentId, userId);
        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
            .body(GlobalResponse.ok(SuccessCode.CREATED, DocumentSnapshotResponse.from(descriptor)));
    }

    @Operation(summary = "문서 스냅샷 메타데이터 조회")
    @GetMapping("/{documentId}/snapshots/{snapshotId}")
    public ResponseEntity<GlobalResponse<DocumentSnapshotResponse>> getSnapshot(
        @PathVariable("documentId") UUID documentId,
        @PathVariable("snapshotId") String snapshotId,
        @CurrentUserId String userId
    ) {
        DocumentSnapshotDescriptor descriptor = documentSnapshotService.describe(documentId, snapshotId, userId);
        return ResponseEntity.ok(GlobalResponse.ok(SuccessCode.SUCCESS, DocumentSnapshotResponse.from(descriptor)));
    }

    @Operation(summary = "문서 스냅샷 다운로드")
    @GetMapping("/{documentId}/snapshots/{snapshotId}/content")
    public ResponseEntity<InputStreamResource> downloadSnapshot(
        @PathVariable("documentId") UUID documentId,
        @PathVariable("snapshotId") String snapshotId,
        @CurrentUserId String userId
    ) {
        DocumentSnapshotContent content = documentSnapshotService.open(documentId, snapshotId, userId);
        MediaType mediaType = MediaType.parseMediaType(content.contentType());
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(content.size())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(content.originalName()).build().toString()
            )
            .body(new InputStreamResource(content.input()));
    }

    @Operation(summary = "문서 스냅샷 삭제")
    @DeleteMapping("/{documentId}/snapshots/{snapshotId}")
    public ResponseEntity<GlobalResponse<Void>> deleteSnapshot(
        @PathVariable("documentId") UUID documentId,
        @PathVariable("snapshotId") String snapshotId,
        @CurrentUserId String userId
    ) {
        documentSnapshotService.delete(documentId, snapshotId, userId);
        return ResponseEntity.ok(GlobalResponse.ok());
    }
}
