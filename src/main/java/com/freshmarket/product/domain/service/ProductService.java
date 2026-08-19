package com.freshmarket.product.domain.service;

import com.freshmarket.product.domain.dto.PageTokens;
import com.freshmarket.product.domain.dto.ProductListItem;
import com.freshmarket.product.domain.dto.ProductListResponse;
import com.freshmarket.product.domain.dto.ProductSearchCondition;
import com.freshmarket.product.domain.dto.ProductWithMinPrice;
import com.freshmarket.product.domain.entity.SaleStatus;
import com.freshmarket.product.domain.repository.ProductQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 회원에게 보이는 상품 조회를 맡는다. 관리자 조회는 AdminProductService 가 따로 맡는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductQueryRepository productQueryRepository;

    /*
     * 상품 목록을 조건에 맞춰 조회한다.
     * 리포지토리가 pageSize + 1 건을 주므로 초과분을 잘라내고 다음 페이지 여부를 판단한다.
     */
    public ProductListResponse getProducts(ProductSearchCondition condition) {
        List<ProductWithMinPrice> found = productQueryRepository.search(condition);

        boolean hasNext = found.size() > condition.pageSize();
        List<ProductWithMinPrice> page = hasNext
                ? found.subList(0, condition.pageSize())
                : found;

        List<ProductListItem> items = page.stream()
                .map(ProductService::toItem)
                .toList();

        return new ProductListResponse(items, nextTokenOf(page, hasNext));
    }

    /*
     * 조회 결과를 응답 표현으로 옮긴다.
     *
     * soldOut 은 원래 가용 재고로 판정해야 하나 stock 도메인이 없어
     * 지금은 saleStatus 로 대신한다. 재고 연동은 후속 이슈다.
     * mainImageUrl 은 product_image 조인과 CDN 설정이 필요해 아직 채우지 않는다.
     */
    private static ProductListItem toItem(ProductWithMinPrice row) {
        return new ProductListItem(
                row.productId(),
                row.name(),
                new ProductListItem.CategorySummary(row.categoryId(), row.categoryName()),
                row.minPrice(),
                row.saleStatus(),
                row.saleStatus() == SaleStatus.SOLD_OUT,
                null);
    }

    /*
     * 다음 페이지 토큰. 마지막 페이지면 null 을 준다 (API-5-01).
     * 마지막 행의 id 를 다음 요청의 커서로 쓴다.
     */
    private static String nextTokenOf(List<ProductWithMinPrice> page, boolean hasNext) {
        if (!hasNext || page.isEmpty()) {
            return null;
        }
        return PageTokens.encode(page.get(page.size() - 1).productId());
    }
}