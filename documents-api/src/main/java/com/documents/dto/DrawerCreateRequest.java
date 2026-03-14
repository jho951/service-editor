package com.documents.dto;

import jakarta.validation.constraints.NotBlank;
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
public class DrawerCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    private Integer width;
    private Integer height;

    @NotBlank(message = "벡터 데이터는 비어 있을 수 없습니다.")
    private String vectorJson;
}
