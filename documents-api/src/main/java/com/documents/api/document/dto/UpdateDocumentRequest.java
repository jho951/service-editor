package com.documents.api.document.dto;

import com.documents.api.document.validation.ValidDocumentMeta;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "문서 수정 요청")
public class UpdateDocumentRequest {

    @Size(max = 255)
    @Schema(description = "문서 제목", example = "수정된 프로젝트 개요", nullable = true)
    private String title;

    @ValidDocumentMeta
    @Schema(description = "문서 아이콘 JSON", nullable = true, example = "{\"type\":\"emoji\",\"value\":\"📄\"}")
    private JsonNode icon;

    @ValidDocumentMeta
    @Schema(description = "문서 커버 JSON", nullable = true, example = "{\"type\":\"image\",\"value\":\"cover-2\"}")
    private JsonNode cover;

    @Schema(description = "부모 문서 ID", nullable = true)
    private UUID parentId;
}
