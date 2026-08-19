package com.freshmarket.product.domain.controller;

import com.freshmarket.product.domain.dto.PageTokens;
import com.freshmarket.product.domain.dto.ProductListResponse;
import com.freshmarket.product.domain.dto.ProductSearchCondition;
import com.freshmarket.product.domain.dto.ProductSortType;
import com.freshmarket.product.domain.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 회원에게 상품 목록을 노출한다
@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/v1/products")
    public ProductListResponse getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false, defaultValue = "CREATED_DESC") ProductSortType sort,
            @RequestParam(required = false) String pageToken,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {

        ProductSearchCondition condition = new ProductSearchCondition(
                categoryId, minPrice, maxPrice, sort, PageTokens.decode(pageToken), pageSize);

        return productService.getProducts(condition);
    }
}