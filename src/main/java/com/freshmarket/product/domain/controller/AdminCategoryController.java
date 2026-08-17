package com.freshmarket.product.domain.controller;

import com.freshmarket.common.response.ResponseEnvelope;
import com.freshmarket.product.domain.dto.CategoryCreateRequest;
import com.freshmarket.product.domain.dto.CategoryResponse;
import com.freshmarket.product.domain.dto.CategoryUpdateRequest;
import com.freshmarket.product.domain.service.AdminCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/categories")
class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    // 카테고리는 트리 구조라 화면에서 항상 전체를 구성해야 한다. 페이지네이션을 넣지 않는다
    @GetMapping
    public ResponseEntity<ResponseEnvelope<List<CategoryResponse>>> findAll() {
        return ResponseEntity.ok(ResponseEnvelope.success(adminCategoryService.findAll()));
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<ResponseEnvelope<CategoryResponse>> findById(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ResponseEnvelope.success(adminCategoryService.findById(categoryId)));
    }

    @PostMapping
    public ResponseEntity<ResponseEnvelope<CategoryResponse>> register(
            @Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = adminCategoryService.register(request.name(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseEnvelope.success(response));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<ResponseEnvelope<CategoryResponse>> rename(
            @PathVariable Long categoryId, @Valid @RequestBody CategoryUpdateRequest request) {
        CategoryResponse response = adminCategoryService.rename(categoryId, request.name());
        return ResponseEntity.ok(ResponseEnvelope.success(response));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ResponseEnvelope<Void>> delete(@PathVariable Long categoryId) {
        adminCategoryService.delete(categoryId);
        return ResponseEntity.ok(ResponseEnvelope.success());
    }
}