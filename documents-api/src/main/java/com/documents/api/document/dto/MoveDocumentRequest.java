package com.documents.api.document.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "문서 이동 요청")
public class MoveDocumentRequest {

	@Schema(description = "이동 대상 부모 문서 ID", nullable = true)
	private UUID targetParentId;

	@Schema(description = "같은 형제 집합의 앞 문서 ID", nullable = true)
	private UUID afterDocumentId;

	@Schema(description = "같은 형제 집합의 뒤 문서 ID", nullable = true)
	private UUID beforeDocumentId;
}
