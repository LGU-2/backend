package com.freshmarket.product.domain.dto;

public record ProductSearchCondition(
        Long categoryId,
        Integer minPrice,
        Integer maxPrice,
        ProductSortType sort,
        PageCursor cursor,
        int pageSize
) {

    private static final ProductSortType DEFAULT_SORT = ProductSortType.CREATED_DESC;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

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
    }
}