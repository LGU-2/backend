package com.freshmarket.product.domain.service;

import com.freshmarket.product.domain.dto.CategoryResponse;
import com.freshmarket.product.domain.entity.Category;
import com.freshmarket.product.domain.exception.ProductErrorCode;
import com.freshmarket.product.domain.exception.ProductException;
import com.freshmarket.product.domain.repository.CategoryRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
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
        String trimmedName = name.trim();
        validateParentExists(parentId);
        if (existsDuplicateName(parentId, trimmedName)) {
            throw new ProductException(ProductErrorCode.CATEGORY_DUPLICATE_NAME);
        }
        Category category = Category.register(trimmedName, parentId);
        try {
            categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            throw new ProductException(ProductErrorCode.CATEGORY_DUPLICATE_NAME, e);
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse rename(Long categoryId, String newName) {
        String trimmedName = newName.trim();
        Category category = getCategoryOrThrow(categoryId);
        boolean nameChanged = !category.getName().equals(trimmedName);
        if (nameChanged && existsDuplicateName(category.getParentId(), trimmedName)) {
            throw new ProductException(ProductErrorCode.CATEGORY_DUPLICATE_NAME);
        }
        try {
            category.rename(trimmedName);
            categoryRepository.flush();   // UPDATE를 지금 실행시켜서 제약 위반을 이 안에서 잡음
        } catch (DataIntegrityViolationException e) {
            throw new ProductException(ProductErrorCode.CATEGORY_DUPLICATE_NAME, e);
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long categoryId) {
        Category category = getCategoryOrThrow(categoryId);
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new ProductException(ProductErrorCode.CATEGORY_HAS_CHILDREN);
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();   // DELETE를 지금 실행시켜서 FK 위반을 이 안에서 잡음
        } catch (DataIntegrityViolationException e) {
            // 하위 카테고리는 위에서 걸러졌으므로, 여기 남는 FK 위반은 소속 상품 때문이다
            throw new ProductException(ProductErrorCode.CATEGORY_HAS_PRODUCTS, e);
        }
    }

    private boolean existsDuplicateName(Long parentId, String name) {
        return parentId == null
                ? categoryRepository.existsByParentIdIsNullAndName(name)
                : categoryRepository.existsByParentIdAndName(parentId, name);
    }

    private void validateParentExists(Long parentId) {
        if (parentId != null && !categoryRepository.existsById(parentId)) {
            throw new ProductException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private Category getCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.CATEGORY_NOT_FOUND));
    }
}