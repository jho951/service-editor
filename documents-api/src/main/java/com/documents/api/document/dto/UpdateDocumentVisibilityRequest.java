package com.documents.api.document.dto;

import com.documents.domain.DocumentVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "문서 공개 상태 수정 요청")
public class UpdateDocumentVisibilityRequest {

    @NotNull
    @Schema(description = "문서 공개 상태", example = "PUBLIC", nullable = false)
    private DocumentVisibility visibility;

    @NotNull
    @Schema(description = "클라이언트가 기준으로 삼은 문서 버전", example = "3", nullable = false)
    private Integer version;
}
