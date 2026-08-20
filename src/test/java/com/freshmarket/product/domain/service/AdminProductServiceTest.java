package com.freshmarket.product.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.product.domain.dto.AdminProductCreateRequest;
import com.freshmarket.product.domain.dto.AdminProductOptionCreateRequest;
import com.freshmarket.product.domain.dto.AdminProductResponse;
import com.freshmarket.product.domain.entity.Product;
import com.freshmarket.product.domain.exception.ProductErrorCode;
import com.freshmarket.product.domain.exception.ProductException;
import com.freshmarket.product.domain.repository.CategoryRepository;
import com.freshmarket.product.domain.repository.ProductOptionRepository;
import com.freshmarket.product.domain.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

// AdminProductService의 등록과 각 실패 케이스를 검증한다
@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private AdminProductService adminProductService;

    @Test
    void 상품과_옵션을_함께_등록한다() {
        // given
        when(categoryRepository.existsById(4L)).thenReturn(true);
        stubSaveAssignsId();
        AdminProductCreateRequest request = new AdminProductCreateRequest(
                "제주 감귤 1kg", 4L, 2L, "COLD", 3, "달콤한 제주 감귤입니다.",
                List.of(new AdminProductOptionCreateRequest("1kg", 12900)));

        // when
        AdminProductResponse result = adminProductService.register(request);

        // then
        assertThat(result.name()).isEqualTo("제주 감귤 1kg");
        assertThat(result.categoryId()).isEqualTo(4L);
        assertThat(result.supplierId()).isEqualTo(2L);
        assertThat(result.storageType()).isEqualTo("COLD");
        assertThat(result.saleAvailableDaysFromExpiry()).isEqualTo(3);
        assertThat(result.description()).isEqualTo("달콤한 제주 감귤입니다.");
        assertThat(result.options()).hasSize(1);
        assertThat(result.options().get(0).name()).isEqualTo("1kg");
        assertThat(result.options().get(0).price()).isEqualTo(12900);
    }

    @Test
    void 판매_가능_최소_잔여일수를_생략하면_0으로_등록된다() {
        // given
        when(categoryRepository.existsById(4L)).thenReturn(true);
        stubSaveAssignsId();
        AdminProductCreateRequest request = new AdminProductCreateRequest(
                "제주 감귤 1kg", 4L, 2L, "COLD", null, null,
                List.of(new AdminProductOptionCreateRequest("1kg", 12900)));

        // when
        AdminProductResponse result = adminProductService.register(request);

        // then
        assertThat(result.saleAvailableDaysFromExpiry()).isEqualTo(0);
    }

    @Test
    void 존재하지_않는_카테고리로_등록하면_실패한다() {
        // given
        when(categoryRepository.existsById(999L)).thenReturn(false);
        AdminProductCreateRequest request = new AdminProductCreateRequest(
                "제주 감귤 1kg", 999L, 2L, "COLD", 3, null,
                List.of(new AdminProductOptionCreateRequest("1kg", 12900)));

        // when, then
        assertThatThrownBy(() -> adminProductService.register(request))
                .isInstanceOf(ProductException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.CATEGORY_NOT_FOUND);
        verify(productRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_공급처로_등록하면_실패한다() {
        // given
        when(categoryRepository.existsById(4L)).thenReturn(true);
        when(productRepository.save(any())).thenThrow(new DataIntegrityViolationException(
                "Cannot add or update a child row: a foreign key constraint fails "
                        + "(`freshmarket`.`product`, CONSTRAINT `fk_product_supplier` "
                        + "FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`supplier_id`))"));
        AdminProductCreateRequest request = new AdminProductCreateRequest(
                "제주 감귤 1kg", 4L, 999L, "COLD", 3, null,
                List.of(new AdminProductOptionCreateRequest("1kg", 12900)));

        // when, then
        assertThatThrownBy(() -> adminProductService.register(request))
                .isInstanceOf(ProductException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.SUPPLIER_NOT_FOUND);
        verify(productOptionRepository, never()).save(any());
    }

    @Test
    void 등록_중_카테고리가_동시에_삭제되면_존재하지_않는_카테고리로_응답한다() {
        // given — 사전 확인(existsById) 통과 직후, save() 시점엔 카테고리가 이미 삭제된 경합 상황
        when(categoryRepository.existsById(4L)).thenReturn(true);
        when(productRepository.save(any())).thenThrow(new DataIntegrityViolationException(
                "Cannot add or update a child row: a foreign key constraint fails "
                        + "(`freshmarket`.`product`, CONSTRAINT `fk_product_category` "
                        + "FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`))"));
        AdminProductCreateRequest request = new AdminProductCreateRequest(
                "제주 감귤 1kg", 4L, 2L, "COLD", 3, null,
                List.of(new AdminProductOptionCreateRequest("1kg", 12900)));

        // when, then
        assertThatThrownBy(() -> adminProductService.register(request))
                .isInstanceOf(ProductException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void 알_수_없는_제약_위반은_그대로_전파된다() {
        // given — product_code 유니크 제약처럼 별도로 변환하지 않는 위반
        when(categoryRepository.existsById(4L)).thenReturn(true);
        DataIntegrityViolationException unknownViolation = new DataIntegrityViolationException(
                "Duplicate entry for key 'uk_product_code'");
        when(productRepository.save(any())).thenThrow(unknownViolation);
        AdminProductCreateRequest request = new AdminProductCreateRequest(
                "제주 감귤 1kg", 4L, 2L, "COLD", 3, null,
                List.of(new AdminProductOptionCreateRequest("1kg", 12900)));

        // when, then
        assertThatThrownBy(() -> adminProductService.register(request))
                .isSameAs(unknownViolation);
    }

    // 실제 저장이 없는 단위 테스트에서 JPA가 채워줄 생성 ID를 대신 채워준다.
    // 옵션 등록(saveOption)이 product.getId()를 그대로 넘겨받아 써야 해서 필요하다.
    private void stubSaveAssignsId() {
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 1L);
            return product;
        });
    }
}
