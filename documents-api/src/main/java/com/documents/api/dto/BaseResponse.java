package com.documents.api.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseResponse {
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
