package com.freshmarket.product.domain.controller;

import com.freshmarket.common.response.CursorPageResponse;
import com.freshmarket.common.response.ResponseEnvelope;
import com.freshmarket.product.domain.dto.PageTokens;
import com.freshmarket.product.domain.dto.ProductListItem;
import com.freshmarket.product.domain.dto.ProductSearchCondition;
import com.freshmarket.product.domain.dto.ProductSortType;
import com.freshmarket.product.domain.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 회원에게 상품 목록을 노출한다
@RestController
@RequiredArgsConstructor
class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 목록 조회",
            description = "카테고리와 가격 구간으로 거르고, 옵션 최저가/최신순으로 정렬하며, "
                    + "커서 기반으로 페이지네이션한다.")
    @GetMapping("/v1/products")
    public ResponseEntity<ResponseEnvelope<CursorPageResponse<ProductListItem>>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false, defaultValue = "CREATED_DESC") ProductSortType sort,
            @RequestParam(required = false) String pageToken,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {

        ProductSearchCondition condition = new ProductSearchCondition(
                categoryId, minPrice, maxPrice, sort, PageTokens.decode(pageToken), pageSize);

        return ResponseEntity.ok(ResponseEnvelope.success(productService.getProducts(condition)));
    }
}