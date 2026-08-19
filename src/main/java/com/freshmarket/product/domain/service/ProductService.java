package com.freshmarket.product.domain.service;

import com.freshmarket.common.response.CursorPageResponse;
import com.freshmarket.product.domain.dto.PageTokens;
import com.freshmarket.product.domain.dto.ProductListItem;
import com.freshmarket.product.domain.dto.ProductSearchCondition;
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

        return CursorPageResponse.of(items, nextTokenOf(page, hasNext));
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

    private static String nextTokenOf(List<ProductWithMinPrice> page, boolean hasNext) {
        if (!hasNext || page.isEmpty()) {
            return null;
        }
        return PageTokens.encode(page.get(page.size() - 1).productId());
    }
}