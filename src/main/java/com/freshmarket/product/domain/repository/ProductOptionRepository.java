package com.freshmarket.product.domain.repository;

import com.freshmarket.product.domain.entity.ProductOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    // 상품 하나에 속한 옵션 전체를 찾는다. 재시도 응답을 재구성할 때 쓰인다
    List<ProductOption> findAllByProductId(Long productId);
}