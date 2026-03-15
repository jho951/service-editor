package com.documents.api.document;

import com.documents.api.code.SuccessCode;
import com.documents.api.document.dto.CreateDocumentRequest;
import com.documents.api.document.dto.DocumentResponse;
import com.documents.api.dto.GlobalResponse;
import com.documents.domain.Document;
import com.documents.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Document", description = "문서 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class DocumentController {

    // TODO: 임시 헤더 값. 인증 서버와 연동 후 수정 필요
    private static final String USER_ID_HEADER = "X-User-Id";

    private final DocumentService documentService;
    private final DocumentApiMapper documentApiMapper;

    @Operation(summary = "문서 생성")
    @PostMapping("/workspaces/{workspaceId}/documents")
    public ResponseEntity<GlobalResponse<DocumentResponse>> createDocument(
            @PathVariable("workspaceId") UUID workspaceId,
            @Valid @RequestBody CreateDocumentRequest request,
            @RequestHeader(USER_ID_HEADER) String userId
    ) {
        Document createdDocument = documentService.create(
                workspaceId,
                request.getParentId(),
                request.getTitle(),
                documentApiMapper.serializeIcon(request),
                documentApiMapper.serializeCover(request),
                userId
        );

        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
                .body(GlobalResponse.ok(SuccessCode.CREATED, documentApiMapper.toResponse(createdDocument)));
    }
}
