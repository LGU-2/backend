package com.freshmarket.product.domain.repository;

import static com.freshmarket.product.domain.entity.QCategory.category;
import static com.freshmarket.product.domain.entity.QProduct.product;
import static com.freshmarket.product.domain.entity.QProductOption.productOption;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.freshmarket.product.domain.dto.ProductSearchCondition;
import com.freshmarket.product.domain.dto.ProductWithMinPrice;
import com.freshmarket.product.domain.dto.QProductWithMinPrice;
import com.freshmarket.product.domain.entity.SaleStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/*
 * QueryDSL 로 짜는 상품 동적 조회 전용 컴포넌트.
 *
 * Spring Data 의 <Repository 인터페이스명>+Impl 자동 결합 관례를 쓰지 않는다.
 * 그 관례를 쓰면 Impl 접미사가 붙어 이 팀의 레포지토리 이름 규칙(DPB-4-10,
 * ArchitectureTest 의 레포지토리_이름)과 충돌한다. 그냥 일반 빈으로 등록해 우회한다.
 */
@Repository
@RequiredArgsConstructor
public class ProductQueryRepository {

    // 옵션 최저가. 판매중인 옵션만 대상으로 한다
    private static final NumberExpression<Integer> MIN_PRICE = productOption.price.min();

    private final JPAQueryFactory queryFactory;

    /*
     * 조건에 맞는 상품을 옵션 최저가와 함께 조회한다.
     * pageSize + 1 건을 가져와 다음 페이지 존재 여부를 판단할 수 있게 한다.
     */
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
                        cursorLt(condition.cursorId()))
                .groupBy(product.id, product.name, category.id, category.name, product.saleStatus)
                .having(
                        minPriceGoe(condition.minPrice()),
                        minPriceLoe(condition.maxPrice()))
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
     * 가격 하한. 집계 결과를 거르는 것이라 where 가 아니라 having 에 둔다.
     * where 에 두면 조건에 맞는 옵션만 남긴 뒤 최저가를 내 다른 값이 나온다.
     */
    private BooleanExpression minPriceGoe(Integer minPrice) {
        return minPrice != null ? MIN_PRICE.goe(minPrice) : null;
    }

    // 가격 상한. 이유는 위와 같다
    private BooleanExpression minPriceLoe(Integer maxPrice) {
        return maxPrice != null ? MIN_PRICE.loe(maxPrice) : null;
    }

    /*
     * 정렬 기준. id 내림차순을 항상 마지막에 붙인다.
     * SALES_DESC 는 daily_sales 가 필요해 statistics 도메인 도입 전까지 CREATED_DESC 로 처리한다.
     */
    private OrderSpecifier<?>[] orderOf(ProductSearchCondition condition) {
        return switch (condition.sort()) {
            case PRICE_ASC -> new OrderSpecifier<?>[]{MIN_PRICE.asc(), product.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{MIN_PRICE.desc(), product.id.desc()};
            case CREATED_DESC, SALES_DESC ->
                    new OrderSpecifier<?>[]{product.createdAt.desc(), product.id.desc()};
        };
    }
}