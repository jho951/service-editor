package com.documents.api.document.dto;

import com.documents.api.block.validation.ValidBlockContent;
import com.documents.service.transaction.DocumentTransactionOperationType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DocumentTransactionOperationRequest {

    @NotBlank
    private String opId;

    @NotNull
    private DocumentTransactionOperationType type;

    private String blockRef;

    private Integer version;

    @ValidBlockContent
    private JsonNode content;

    private String parentRef;

    private String afterRef;

    private String beforeRef;

    @AssertTrue
    public boolean isValidOperationShape() {
        if (type == null) {
            return true;
        }

        if (type == DocumentTransactionOperationType.BLOCK_CREATE) {
            return hasText(blockRef)
                    && version == null
                    && content == null;
        }

        if (type == DocumentTransactionOperationType.BLOCK_REPLACE_CONTENT) {
            return hasText(blockRef)
                    && content != null
                    && parentRef == null
                    && afterRef == null
                    && beforeRef == null;
        }

        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
