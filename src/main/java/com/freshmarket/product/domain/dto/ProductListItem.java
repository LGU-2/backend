package com.freshmarket.product.domain.dto;

import com.freshmarket.product.domain.entity.SaleStatus;

// 상품 목록의 항목 하나. 엔티티를 그대로 내보내지 않기 위한 표현이다
public record ProductListItem(
        Long productId,
        String name,
        CategorySummary category,
        Integer minPrice,
        SaleStatus saleStatus,
        boolean soldOut,
        String mainImageUrl
) {

    // 목록에 딸려 나가는 최소 카테고리 정보
    public record CategorySummary(Long categoryId, String name) {
    }
}