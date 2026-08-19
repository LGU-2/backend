package com.freshmarket.product.domain.dto;

/*
 * 상품 목록 조회 조건. 컨트롤러가 받은 요청 파라미터를 리포지토리까지 옮긴다.
 * null 인 필드는 조건에서 빠진다 (ProductRepositoryImpl 참고).
 */
public record ProductSearchCondition(
        Long categoryId,
        Integer minPrice,
        Integer maxPrice,
        ProductSortType sort,
        Long cursorId,
        int pageSize
) {

    private static final ProductSortType DEFAULT_SORT = ProductSortType.CREATED_DESC;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /*
     * 컴팩트 생성자. 기본값 적용과 상한 제한을 여기 모은다.
     * record 는 생성 경로가 하나뿐이라 어느 방향으로 만들어도 이 검사를 지난다.
     */
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