package com.freshmarket.product.domain;

import com.freshmarket.product.ProductApi;
import com.freshmarket.product.domain.repository.ProductOptionRepository;
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
}
