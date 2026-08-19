package com.freshmarket.product.domain.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.freshmarket.product.domain.entity.SaleStatus;

/*
 * 상품 조회 결과. 옵션 최저가는 product 에 없어 조인 집계로 얻는다.
 * @QueryProjection 이 붙으면 QueryDSL 이 이 record 를 직접 생성하는 Q 클래스를 만든다.
 */
public record ProductWithMinPrice(
        Long productId,
        String name,
        Long categoryId,
        String categoryName,
        Integer minPrice,
        SaleStatus saleStatus
) {

    @QueryProjection
    public ProductWithMinPrice {
    }
}