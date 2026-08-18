package com.freshmarket.product.domain.dto;

import com.freshmarket.product.domain.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1") Long id,
        @Schema(description = "카테고리 이름", example = "채소") String name,
        @Schema(description = "상위 카테고리 ID. 최상위 카테고리면 null이다", example = "null") Long parentId
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getParentId());
    }
}