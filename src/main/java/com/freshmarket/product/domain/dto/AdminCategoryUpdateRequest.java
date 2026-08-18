package com.freshmarket.product.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCategoryUpdateRequest(
        @Schema(description = "바꿀 카테고리 이름", example = "과일") @NotBlank @Size(max = 50) String name
) {
}
