package com.freshmarket.product.domain.controller;

import com.freshmarket.common.response.ResponseEnvelope;
import com.freshmarket.product.domain.dto.CategoryResponse;
import com.freshmarket.product.domain.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원에게 카테고리 목록을 노출한다
@RestController
@RequiredArgsConstructor
class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록 조회", description = "현재는 최상위 카테고리만 반환한다.")
    @GetMapping("/v1/categories")
    public ResponseEntity<ResponseEnvelope<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ResponseEnvelope.success(categoryService.getCategories()));
    }
}