package com.freshmarket.product.domain.repository;

import com.freshmarket.product.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

// Category 엔티티에 대한 조회/저장을 담당한다
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 같은 부모 아래에 같은 이름의 카테고리가 있는지 확인한다 (하위 카테고리 이름 중복 검사)
    boolean existsByParentIdAndName(Long parentId, String name);

    // 최상위 카테고리끼리 같은 이름이 있는지 확인한다 (parentId가 null인 것끼리 비교)
    boolean existsByParentIdIsNullAndName(String name);

    // 이 카테고리를 부모로 하는 하위 카테고리가 하나라도 있는지 확인한다 (삭제 시 사용)
    boolean existsByParentId(Long parentId);
}
