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
        /*
         * query 는 검색 엔드포인트에서만 채워진다. 목록 조회는 항상 null 이다.
         * 컨트롤러에서 Bean Validation(@Size)으로 이미 막지만, 다른 경로로 이 record 가
         * 직접 생성될 경우를 대비해 여기서도 다시 검증한다 (SEC-3-03).
         */
        if (query != null) {
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