package com.freshmarket.product.domain.dto;

import java.util.List;

/*
 * 상품 목록 응답. nextPageToken 이 비어 있으면 마지막 페이지다 (API-5-01).
 * 토큰은 클라이언트가 해석할 수 없는 불투명 문자열이다 (API-5-02).
 */
public record ProductListResponse(
        List<ProductListItem> products,
        String nextPageToken
) {
}