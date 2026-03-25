package com.documents.api.document.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "휴지통 문서 응답")
public class TrashDocumentResponse {

	@Schema(description = "문서 ID")
	private UUID documentId;

	@Schema(description = "문서 제목")
	private String title;

	@Schema(description = "부모 문서 ID", nullable = true)
	private UUID parentId;

	@Schema(description = "휴지통 이동 시각")
	private LocalDateTime deletedAt;

	@Schema(description = "자동 영구 삭제 예정 시각")
	private LocalDateTime purgeAt;
}
