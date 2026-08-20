package com.freshmarket.product.domain.service;

import com.freshmarket.product.domain.dto.AdminProductCreateRequest;
import com.freshmarket.product.domain.dto.AdminProductOptionCreateRequest;
import com.freshmarket.product.domain.dto.AdminProductOptionResponse;
import com.freshmarket.product.domain.dto.AdminProductResponse;
import com.freshmarket.product.domain.entity.Product;
import com.freshmarket.product.domain.entity.ProductOption;
import com.freshmarket.product.domain.entity.StorageType;
import com.freshmarket.product.domain.exception.ProductErrorCode;
import com.freshmarket.product.domain.exception.ProductException;
import com.freshmarket.product.domain.repository.CategoryRepository;
import com.freshmarket.product.domain.repository.ProductOptionRepository;
import com.freshmarket.product.domain.repository.ProductRepository;
import java.security.SecureRandom;
import java.time.Year;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 화면에서 상품과 옵션을 등록하는 기능을 담당한다
@Service
@Transactional(readOnly = true)
public class AdminProductService {

    private static final String CODE_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_RANDOM_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final CategoryRepository categoryRepository;

    public AdminProductService(ProductRepository productRepository,
            ProductOptionRepository productOptionRepository,
            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.productOptionRepository = productOptionRepository;
        this.categoryRepository = categoryRepository;
    }

    // 상품과 옵션들을 함께 등록한다. 카테고리는 사전 확인하고, 공급처는 DB 제약으로 확인한다
    @Transactional
    public AdminProductResponse register(AdminProductCreateRequest request) {
        validateCategoryExists(request.categoryId());
        StorageType storageType = StorageType.valueOf(request.storageType());
        int saleAvailableDaysFromExpiry = resolveSaleAvailableDaysFromExpiry(request.saleAvailableDaysFromExpiry());

        Product product = Product.register(generateProductCode(), request.name(), request.categoryId(),
                request.supplierId(), storageType, saleAvailableDaysFromExpiry, request.description());
        try {
            productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            throw resolveSaveException(e);
        }

        List<AdminProductOptionResponse> optionResponses = registerOptions(product.getId(), request.options());
        return AdminProductResponse.of(product, optionResponses);
    }

    // 카테고리가 실재하는지 미리 확인한다. 카테고리 등록 때 상위 카테고리 존재를 확인했던 것과 같은 패턴이다
    private void validateCategoryExists(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ProductException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    // 생략된 값은 0으로 취급한다 (product.md: "0 이상. 기본 0")
    private int resolveSaleAvailableDaysFromExpiry(Integer saleAvailableDaysFromExpiry) {
        return saleAvailableDaysFromExpiry != null ? saleAvailableDaysFromExpiry : 0;
    }

    // 옵션들을 하나씩 저장한다. saveAll()로 묶지 않고 개별 저장하는 이유는 register()와 동일(JPA-3, cascade 없이 각 저장을 추적 가능하게)
    private List<AdminProductOptionResponse> registerOptions(Long productId,
            List<AdminProductOptionCreateRequest> optionRequests) {
        return optionRequests.stream()
                .map(optionRequest -> saveOption(productId, optionRequest))
                .map(AdminProductOptionResponse::from)
                .toList();
    }

    // 옵션 하나를 생성해 저장한다. 같은 상품 안에서 옵션명이 겹치면 OPTION_DUPLICATE_NAME으로 바꾼다
    private ProductOption saveOption(Long productId, AdminProductOptionCreateRequest optionRequest) {
        ProductOption option = ProductOption.register(productId, optionRequest.name(), optionRequest.price());
        try {
            productOptionRepository.save(option);
        } catch (DataIntegrityViolationException e) {
            if (isConstraintViolation(e, "uk_option_product_name")) {
                throw new ProductException(ProductErrorCode.OPTION_DUPLICATE_NAME, e);
            }
            throw e;
        }
        return option;
    }

    /*
     * Supplier 리포지토리가 아직 없어 supplierId를 미리 확인할 방법이 없다. 대신 저장 시점에
     * fk_product_supplier 위반을 잡아 SUPPLIER_NOT_FOUND로 바꾼다.
     * categoryId는 위에서 이미 확인했으므로 fk_product_category 위반은 그 사이 카테고리가
     * 삭제된 경합 상황에서만 발생한다.
     * product_code(uk_product_code) 위반은 영숫자 6자리 무작위 값이라 사실상 일어나지 않는다.
     * 재시도는 넣지 않았다 — 같은 트랜잭션에서 flush 실패 후 계속 쓰면 영속성 컨텍스트가
     * 불안정해질 수 있어, 일어나지도 않을 경합을 막으려 위험을 감수할 이유가 없다.
     */
    private ProductException resolveSaveException(DataIntegrityViolationException e) {
        if (isConstraintViolation(e, "fk_product_supplier")) {
            return new ProductException(ProductErrorCode.SUPPLIER_NOT_FOUND, e);
        }
        if (isConstraintViolation(e, "fk_product_category")) {
            return new ProductException(ProductErrorCode.CATEGORY_NOT_FOUND, e);
        }
        throw e;
    }

    // DB 예외의 근본 원인 메시지에 제약 이름이 들어있는지로 어떤 제약을 위반했는지 구분한다
    private boolean isConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }

    // P-{연도}-{영숫자 6자리 랜덤}. product_id에 의존하지 않아 INSERT 전에 완성할 수 있다
    private String generateProductCode() {
        StringBuilder suffix = new StringBuilder(CODE_RANDOM_LENGTH);
        for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
            suffix.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return "P-" + Year.now().getValue() + "-" + suffix;
    }
}
