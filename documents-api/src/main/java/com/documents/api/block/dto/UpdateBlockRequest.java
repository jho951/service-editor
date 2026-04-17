package com.documents.api.block.dto;

import com.documents.api.block.validation.ValidBlockContent;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "블록 수정 요청")
public class UpdateBlockRequest {

    @NotNull
    @ValidBlockContent
    @Schema(description = "TEXT 블록 content", nullable = false)
    private JsonNode content;

    @NotNull
    @Schema(description = "클라이언트가 기준으로 삼은 블록 버전", example = "3")
    private Integer version;
}
