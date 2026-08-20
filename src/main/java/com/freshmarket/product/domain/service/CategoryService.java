package com.freshmarket.product.domain.service;

import com.freshmarket.product.domain.dto.CategoryResponse;
import com.freshmarket.product.domain.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 회원에게 카테고리 목록을 노출한다. 관리자 관리(등록/수정/삭제)는 AdminCategoryService 가 맡는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /*
     * 카테고리 전체를 조회한다. 지금은 최상위 카테고리 5종만 시드되어 있어
     * 계층 정렬이나 트리 조립 없이 그대로 내려간다. 하위 카테고리가 추가되면
     * parentId 기준 정렬/그룹핑이 필요해질 수 있다.
     */
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}