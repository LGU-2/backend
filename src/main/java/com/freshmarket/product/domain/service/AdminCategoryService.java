package com.freshmarket.product.domain.service;

import com.freshmarket.product.domain.dto.CategoryResponse;
import com.freshmarket.product.domain.entity.Category;
import com.freshmarket.product.domain.exception.ProductErrorCode;
import com.freshmarket.product.domain.exception.ProductException;
import com.freshmarket.product.domain.repository.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;

    public AdminCategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse findById(Long categoryId) {
        return CategoryResponse.from(getCategoryOrThrow(categoryId));
    }

    @Transactional
    public CategoryResponse register(String name, Long parentId) {
        if (categoryRepository.existsByParentIdAndName(parentId, name)) {
            throw new ProductException(ProductErrorCode.CATEGORY_DUPLICATE_NAME);
        }
        Category category = Category.register(name, parentId);
        categoryRepository.save(category);
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse rename(Long categoryId, String newName) {
        Category category = getCategoryOrThrow(categoryId);
        boolean nameChanged = !category.getName().equals(newName);
        if (nameChanged && categoryRepository.existsByParentIdAndName(category.getParentId(), newName)) {
            throw new ProductException(ProductErrorCode.CATEGORY_DUPLICATE_NAME);
        }
        category.rename(newName);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long categoryId) {
        Category category = getCategoryOrThrow(categoryId);
        // 상품 도메인 완료 후 소속 상품 존재 검증 추가 — ProductErrorCode.CATEGORY_HAS_PRODUCTS
        categoryRepository.delete(category);
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.CATEGORY_NOT_FOUND));
    }
}