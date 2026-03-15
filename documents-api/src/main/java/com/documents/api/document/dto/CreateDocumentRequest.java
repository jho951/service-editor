package com.documents.api.document.dto;

import com.documents.api.document.validation.ValidDocumentMeta;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "문서 생성 요청")
public class CreateDocumentRequest {

    @Schema(description = "부모 문서 ID", nullable = true)
    private UUID parentId;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "문서 제목", example = "프로젝트 개요")
    private String title;

    @ValidDocumentMeta
    @Schema(description = "문서 아이콘 JSON", nullable = true, example = "{\"type\":\"emoji\",\"value\":\"📄\"}")
    private JsonNode icon;

    @ValidDocumentMeta
    @Schema(description = "문서 커버 JSON", nullable = true, example = "{\"type\":\"image\",\"value\":\"cover-1\"}")
    private JsonNode cover;
}
