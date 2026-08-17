package com.freshmarket.product.domain.repository;

import com.freshmarket.product.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByParentIdAndName(Long parentId, String name);
}