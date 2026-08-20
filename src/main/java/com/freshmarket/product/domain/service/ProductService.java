package com.freshmarket.product.domain.service;

import com.freshmarket.common.response.CursorPageResponse;
import com.freshmarket.product.domain.dto.PageCursor;
import com.freshmarket.product.domain.dto.PageTokens;
import com.freshmarket.product.domain.dto.ProductListItem;
import com.freshmarket.product.domain.dto.ProductSearchCondition;
import com.freshmarket.product.domain.dto.ProductSortType;
import com.freshmarket.product.domain.dto.ProductWithMinPrice;
import com.freshmarket.product.domain.entity.SaleStatus;
import com.freshmarket.product.domain.repository.ProductQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductQueryRepository productQueryRepository;

    public CursorPageResponse<ProductListItem> getProducts(ProductSearchCondition condition) {
        List<ProductWithMinPrice> found = productQueryRepository.search(condition);

        boolean hasNext = found.size() > condition.pageSize();
        List<ProductWithMinPrice> page = hasNext
                ? found.subList(0, condition.pageSize())
                : found;

        List<ProductListItem> items = page.stream()
                .map(ProductService::toItem)
                .toList();

        return CursorPageResponse.of(items, nextTokenOf(page, hasNext, condition.sort()));
    }

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
     * 다음 페이지 토큰. 마지막 행의 정렬 기준값과 id 로 커서를 만든다.
     * 정렬 축(가격/생성일)에 맞는 값을 넣어야 다음 페이지에서 리포지토리가
     * 같은 축으로 비교할 수 있다 (API-3-04).
     */
    private static String nextTokenOf(
            List<ProductWithMinPrice> page, boolean hasNext, ProductSortType sort) {
        if (!hasNext || page.isEmpty()) {
            return null;
        }
        ProductWithMinPrice last = page.get(page.size() - 1);
        String sortValue = switch (sort) {
            case PRICE_ASC, PRICE_DESC -> String.valueOf(last.minPrice());
            case CREATED_DESC, SALES_DESC -> last.createdAt().toString();
        };
        return PageTokens.encode(new PageCursor(last.productId(), sortValue));
    }
}