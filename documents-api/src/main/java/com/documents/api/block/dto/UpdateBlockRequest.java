package com.documents.api.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "블록 수정 요청")
public class UpdateBlockRequest {

    @NotBlank
    @Size(max = 10000)
    @Schema(description = "TEXT 블록 본문", example = "수정된 블록")
    private String text;

    @NotNull
    @Schema(description = "클라이언트가 기준으로 삼은 블록 버전", example = "3")
    private Integer version;
}
