package com.freshmarket.product.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCategoryCreateRequest(
        @Schema(description = "카테고리 이름", example = "채소") @NotBlank @Size(max = 50) String name,
        @Schema(description = "상위 카테고리 ID. 최상위 카테고리면 생략한다", example = "1") Long parentId
) {
}
