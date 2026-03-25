package com.documents.api.document.dto;

import com.documents.api.document.validation.ValidDocumentMeta;
import com.fasterxml.jackson.databind.JsonNode;

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
@Schema(description = "문서 수정 요청")
public class UpdateDocumentRequest {

	@NotBlank
	@Size(max = 255)
	@Schema(description = "문서 제목", example = "수정된 프로젝트 개요", nullable = true)
	private String title;

	@ValidDocumentMeta
	@Schema(description = "문서 아이콘 JSON", nullable = true, example = "{\"type\":\"emoji\",\"value\":\"📄\"}")
	private JsonNode icon;

	@ValidDocumentMeta
	@Schema(description = "문서 커버 JSON", nullable = true, example = "{\"type\":\"image\",\"value\":\"cover-2\"}")
	private JsonNode cover;

	@NotNull
	@Schema(description = "클라이언트가 기준으로 삼은 문서 버전", example = "3", nullable = false)
	private Integer version;
}
