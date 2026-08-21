package com.freshmarket.product.domain;

import com.freshmarket.product.ProductApi;
import com.freshmarket.product.domain.entity.ProductOption;
import com.freshmarket.product.domain.repository.ProductOptionRepository;
import java.util.List;
import org.springframework.stereotype.Component;

// ProductApi 구현체. package-private로 감춰 다른 도메인이 이 클래스를 직접 참조하지 못하게 한다
@Component
class ProductApiImpl implements ProductApi {

    private final ProductOptionRepository productOptionRepository;

    ProductApiImpl(ProductOptionRepository productOptionRepository) {
        this.productOptionRepository = productOptionRepository;
    }

    // optionId가 존재하기만 하는지가 아니라 productId 소속인지까지 리포지토리 파생 쿼리로 한 번에 확인한다
    @Override
    public boolean existsOption(Long productId, Long optionId) {
        return productOptionRepository.existsByIdAndProductId(optionId, productId);
    }

    // 재시도 응답 재구성용으로 이미 있는 findAllByProductId를 그대로 재사용해 ID만 뽑는다
    @Override
    public List<Long> findOptionIds(Long productId) {
        return productOptionRepository.findAllByProductId(productId).stream()
                .map(ProductOption::getId)
                .toList();
    }
}
