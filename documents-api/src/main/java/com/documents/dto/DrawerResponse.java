package com.documents.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DrawerResponse {
    private UUID id;
    private String title;
    private Integer width;
    private Integer height;
    private String vectorJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
