package com.freshmarket.product.domain.repository;

import com.freshmarket.product.domain.entity.ProductOption;
import com.freshmarket.product.domain.entity.SaleStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    // 회원 조회용. OFF_SALE(판매안함) 옵션은 제외한다. 품절은 표시만 하고 노출은 유지한다
    List<ProductOption> findByProductIdAndSaleStatusNot(Long productId, SaleStatus saleStatus);
}