package com.documents.api.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DocumentTransactionRequest {

    @NotBlank
    private String clientId;

    @NotBlank
    private String batchId;

    @Valid
    @NotEmpty
    private List<DocumentTransactionOperationRequest> operations;
}
