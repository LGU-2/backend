package com.freshmarket.product.domain.repository;

import static com.freshmarket.product.domain.entity.QCategory.category;
import static com.freshmarket.product.domain.entity.QProduct.product;
import static com.freshmarket.product.domain.entity.QProductOption.productOption;

import com.freshmarket.product.domain.dto.ProductSearchCondition;
import com.freshmarket.product.domain.dto.ProductSortType;
import com.freshmarket.product.domain.dto.ProductWithMinPrice;
import com.freshmarket.product.domain.dto.QProductWithMinPrice;
import com.freshmarket.product.domain.entity.SaleStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductQueryRepository {

    // 옵션 최저가. OFF_SALE(비활성) 옵션만 제외한다. SOLD_OUT 은 화면에 노출하되
    // 품절 표시(soldOut)로 구분해야 하므로 여기서 걸러내지 않는다
    private static final NumberExpression<Integer> MIN_PRICE = productOption.price.min();

    private final JPAQueryFactory queryFactory;

    public List<ProductWithMinPrice> search(ProductSearchCondition condition) {
        return queryFactory
                .select(new QProductWithMinPrice(
                        product.id,
                        product.name,
                        category.id,
                        category.name,
                        MIN_PRICE,
                        product.saleStatus,
                        product.createdAt))
                .from(product)
                .join(category).on(category.id.eq(product.categoryId))
                .join(productOption).on(
                        productOption.productId.eq(product.id),
                        productOption.saleStatus.ne(SaleStatus.OFF_SALE))
                .where(
                        product.deletedAt.isNull(),
                        categoryIdEq(condition.categoryId()),
                        priceGoe(condition.minPrice()),
                        priceLoe(condition.maxPrice()),
                        createdAtCursorLt(condition))
                .groupBy(product.id, product.name, category.id, category.name,
                        product.saleStatus, product.createdAt)
                .having(priceCursor(condition))
                .orderBy(orderOf(condition))
                .limit(condition.pageSize() + 1L)
                .fetch();
    }

    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId != null ? product.categoryId.eq(categoryId) : null;
    }

    // 옵션 가격 하한/상한. "이 가격대 옵션이 있는 상품을 찾는다"는 의도라 옵션 단위로 건다
    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice != null ? productOption.price.goe(minPrice) : null;
    }

    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice != null ? productOption.price.loe(maxPrice) : null;
    }

    /*
     * created_at 계열 정렬(CREATED_DESC, SALES_DESC)의 커서 조건.
     * product.createdAt 은 상품마다 하나뿐인 값이라 그룹화 전 WHERE 에서 걸러도 결과가 같다.
     * 내림차순이라 "다음 페이지"는 커서보다 작은 값이고, 값이 같으면 id 로 가른다
     * (동점 처리 규칙은 orderOf() 의 tie-break, product.id.desc() 와 짝을 맞춘다).
     */
    private BooleanExpression createdAtCursorLt(ProductSearchCondition condition) {
        if (isPriceSort(condition.sort()) || condition.cursor() == null) {
            return null;
        }
        LocalDateTime cursorCreatedAt = LocalDateTime.parse(condition.cursor().sortValue());
        Long cursorId = condition.cursor().id();
        return product.createdAt.lt(cursorCreatedAt)
                .or(product.createdAt.eq(cursorCreatedAt).and(product.id.lt(cursorId)));
    }

    /*
     * 가격 계열 정렬(PRICE_ASC, PRICE_DESC)의 커서 조건.
     * MIN_PRICE 는 그룹화 결과에서만 값을 아는 집계라 HAVING 에 둔다.
     * product.id 는 groupBy 대상이라 HAVING 에서도 참조할 수 있다.
     * 오름차순은 "다음 페이지"가 커서보다 큰 값, 내림차순은 작은 값이다.
     */
    private BooleanExpression priceCursor(ProductSearchCondition condition) {
        if (!isPriceSort(condition.sort()) || condition.cursor() == null) {
            return null;
        }
        int cursorPrice = Integer.parseInt(condition.cursor().sortValue());
        Long cursorId = condition.cursor().id();
        BooleanExpression primary = condition.sort() == ProductSortType.PRICE_ASC
                ? MIN_PRICE.gt(cursorPrice)
                : MIN_PRICE.lt(cursorPrice);
        return primary.or(MIN_PRICE.eq(cursorPrice).and(product.id.lt(cursorId)));
    }

    private boolean isPriceSort(ProductSortType sort) {
        return sort == ProductSortType.PRICE_ASC || sort == ProductSortType.PRICE_DESC;
    }

    private OrderSpecifier<?>[] orderOf(ProductSearchCondition condition) {
        return switch (condition.sort()) {
            case PRICE_ASC -> new OrderSpecifier<?>[]{MIN_PRICE.asc(), product.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{MIN_PRICE.desc(), product.id.desc()};
            case CREATED_DESC, SALES_DESC ->
                    new OrderSpecifier<?>[]{product.createdAt.desc(), product.id.desc()};
        };
    }
}