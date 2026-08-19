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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductQueryRepository {

    // 옵션 최저가. 판매중인 옵션만 대상으로 한다
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
                        product.saleStatus))
                .from(product)
                .join(category).on(category.id.eq(product.categoryId))
                .join(productOption).on(
                        productOption.productId.eq(product.id),
                        productOption.saleStatus.ne(SaleStatus.OFF_SALE))
                .where(
                        product.deletedAt.isNull(),
                        categoryIdEq(condition.categoryId()),
                        cursorLt(condition.cursorId()),
                        priceGoe(condition.minPrice()),
                        priceLoe(condition.maxPrice()))
                .groupBy(product.id, product.name, category.id, category.name, product.saleStatus)
                .orderBy(orderOf(condition))
                .limit(condition.pageSize() + 1L)
                .fetch();
    }

    // 카테고리 조건. null 이면 where 절에서 빠져 전체 카테고리를 본다
    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId != null ? product.categoryId.eq(categoryId) : null;
    }

    // 커서 조건. 첫 페이지는 null 이라 빠지고, 다음 페이지부터 직전 마지막 id 보다 작은 행만 본다
    private BooleanExpression cursorLt(Long cursorId) {
        return cursorId != null ? product.id.lt(cursorId) : null;
    }

    /*
     * 가격 하한. 옵션 단위로 거른다 — "이 가격대 옵션이 있는 상품"을 찾는 필터다.
     * where 에 두므로, 조건에 안 맞는 옵션은 이 상품의 그룹에서 제외된 채 최저가가 계산된다.
     * 즉 응답의 minPrice 는 "조건을 만족하는 옵션들 중 최저가"이며, 상품의 절대 최저가와
     * 다를 수 있다. (예: 4만원 이상 필터 시, 1kg=12900원 옵션이 있어도 상품 자체는 노출되고
     * minPrice 는 조건을 만족하는 옵션 중 최저값으로 계산된다)
     */
    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice != null ? productOption.price.goe(minPrice) : null;
    }

    // 가격 상한. 이유는 위와 같다
    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice != null ? productOption.price.loe(maxPrice) : null;
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