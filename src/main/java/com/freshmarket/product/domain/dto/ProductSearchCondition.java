package com.freshmarket.product.domain.dto;

public record ProductSearchCondition(
        Long categoryId,
        Integer minPrice,
        Integer maxPrice,
        String query,
        ProductSortType sort,
        PageCursor cursor,
        int pageSize
) {

    private static final ProductSortType DEFAULT_SORT = ProductSortType.CREATED_DESC;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;

    public ProductSearchCondition {
        if (sort == null) {
            sort = DEFAULT_SORT;
        }
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (minPrice != null && minPrice < 0) {
            throw new IllegalArgumentException("minPrice 는 0 이상이어야 한다: " + minPrice);
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new IllegalArgumentException("maxPrice 는 0 이상이어야 한다: " + maxPrice);
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException(
                    "minPrice 가 maxPrice 보다 클 수 없다: " + minPrice + " > " + maxPrice);
        }
        if (query != null) {
            query = query.strip();   // 검증 전에 트림 (FUN-3-01)
            if (query.isBlank()) {
                throw new IllegalArgumentException("query 는 공백일 수 없다");
            }
            if (query.length() > MAX_QUERY_LENGTH) {
                throw new IllegalArgumentException(
                        "query 는 " + MAX_QUERY_LENGTH + "자를 넘을 수 없다: " + query.length());
            }
        }
    }
}