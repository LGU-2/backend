package com.freshmarket.product.domain;

import com.freshmarket.product.ProductApi;
import com.freshmarket.product.domain.repository.ProductOptionRepository;
import org.springframework.stereotype.Component;

@Component
class ProductApiImpl implements ProductApi {

    private final ProductOptionRepository productOptionRepository;

    ProductApiImpl(ProductOptionRepository productOptionRepository) {
        this.productOptionRepository = productOptionRepository;
    }

    @Override
    public boolean existsOption(Long productId, Long optionId) {
        return productOptionRepository.existsByIdAndProductId(optionId, productId);
    }
}
