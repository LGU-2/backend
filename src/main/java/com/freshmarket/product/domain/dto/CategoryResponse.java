package com.freshmarket.product.domain.dto;

import com.freshmarket.product.domain.entity.Category;

public record CategoryResponse(Long id, String name, Long parentId) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getParentId());
    }
}