package com.freshmarket.product.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.freshmarket.common.response.CursorPageResponse;
import com.freshmarket.product.domain.dto.PageCursor;
import com.freshmarket.product.domain.dto.PageTokens;
import com.freshmarket.product.domain.dto.ProductListItem;
import com.freshmarket.product.domain.dto.ProductSearchCondition;
import com.freshmarket.product.domain.dto.ProductSortType;
import com.freshmarket.product.domain.dto.ProductWithMinPrice;
import com.freshmarket.product.domain.entity.SaleStatus;
import com.freshmarket.product.domain.repository.ProductQueryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// ProductService의 목록 조회와 페이징, 응답 변환 분기를 검증한다
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 10, 0);

    @Mock
    private ProductQueryRepository productQueryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void 조건에_맞는_상품_목록을_조회한다() {
        // given
        ProductSearchCondition condition = new ProductSearchCondition(
                4L, null, null, ProductSortType.CREATED_DESC, null, 20);
        when(productQueryRepository.search(condition)).thenReturn(List.of(
                new ProductWithMinPrice(1L, "감귤", 4L, "과일", 12900, SaleStatus.ON_SALE, NOW)));

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("감귤");
        assertThat(result.items().get(0).minPrice()).isEqualTo(12900);
    }

    @Test
    void 결과가_pageSize보다_많으면_다음_페이지_토큰을_준다() {
        // given — pageSize 2 인데 리포지토리가 3건(= pageSize + 1)을 준 상황
        ProductSearchCondition condition = new ProductSearchCondition(
                null, null, null, ProductSortType.CREATED_DESC, null, 2);
        when(productQueryRepository.search(condition)).thenReturn(List.of(
                new ProductWithMinPrice(3L, "상품3", 1L, "카테고리", 1000, SaleStatus.ON_SALE, NOW),
                new ProductWithMinPrice(2L, "상품2", 1L, "카테고리", 2000, SaleStatus.ON_SALE, NOW),
                new ProductWithMinPrice(1L, "상품1", 1L, "카테고리", 3000, SaleStatus.ON_SALE, NOW)));

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then — 초과분(1건)은 잘리고, 페이지의 마지막 행(id=2) 기준으로 토큰이 만들어진다
        assertThat(result.items()).hasSize(2);
        assertThat(result.nextPageToken()).isNotNull();
        PageCursor decoded = PageTokens.decode(result.nextPageToken());
        assertThat(decoded.id()).isEqualTo(2L);
        assertThat(decoded.sortValue()).isEqualTo(NOW.toString());
    }

    @Test
    void 결과가_pageSize_이하면_다음_페이지_토큰이_없다() {
        // given
        ProductSearchCondition condition = new ProductSearchCondition(
                null, null, null, ProductSortType.CREATED_DESC, null, 20);
        when(productQueryRepository.search(condition)).thenReturn(List.of(
                new ProductWithMinPrice(1L, "감귤", 4L, "과일", 12900, SaleStatus.ON_SALE, NOW)));

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then
        assertThat(result.nextPageToken()).isNull();
    }

    @Test
    void 조회_결과가_없으면_빈_목록과_토큰_없음을_준다() {
        // given
        ProductSearchCondition condition = new ProductSearchCondition(
                999L, null, null, ProductSortType.CREATED_DESC, null, 20);
        when(productQueryRepository.search(condition)).thenReturn(List.of());

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.nextPageToken()).isNull();
    }

    @Test
    void 품절_상품은_soldOut이_true로_내려간다() {
        // given
        ProductSearchCondition condition = new ProductSearchCondition(
                null, null, null, ProductSortType.CREATED_DESC, null, 20);
        when(productQueryRepository.search(condition)).thenReturn(List.of(
                new ProductWithMinPrice(1L, "감귤", 4L, "과일", 12900, SaleStatus.SOLD_OUT, NOW)));

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then
        assertThat(result.items().get(0).soldOut()).isTrue();
    }

    @Test
    void 판매중_상품은_soldOut이_false로_내려간다() {
        // given
        ProductSearchCondition condition = new ProductSearchCondition(
                null, null, null, ProductSortType.CREATED_DESC, null, 20);
        when(productQueryRepository.search(condition)).thenReturn(List.of(
                new ProductWithMinPrice(1L, "감귤", 4L, "과일", 12900, SaleStatus.ON_SALE, NOW)));

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then
        assertThat(result.items().get(0).soldOut()).isFalse();
    }

    @Test
    void 카테고리_정보가_요약되어_내려간다() {
        // given
        ProductSearchCondition condition = new ProductSearchCondition(
                null, null, null, ProductSortType.CREATED_DESC, null, 20);
        when(productQueryRepository.search(condition)).thenReturn(List.of(
                new ProductWithMinPrice(1L, "감귤", 4L, "과일", 12900, SaleStatus.ON_SALE, NOW)));

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then
        assertThat(result.items().get(0).category().categoryId()).isEqualTo(4L);
        assertThat(result.items().get(0).category().name()).isEqualTo("과일");
    }

    @Test
    void 가격_정렬이면_최저가_기준으로_다음_페이지_토큰을_만든다() {
        // given
        ProductSearchCondition condition = new ProductSearchCondition(
                null, null, null, ProductSortType.PRICE_ASC, null, 1);
        when(productQueryRepository.search(condition)).thenReturn(List.of(
                new ProductWithMinPrice(1L, "감귤", 4L, "과일", 12900, SaleStatus.ON_SALE, NOW),
                new ProductWithMinPrice(2L, "복숭아", 4L, "과일", 20000, SaleStatus.ON_SALE, NOW)));

        // when
        CursorPageResponse<ProductListItem> result = productService.getProducts(condition);

        // then — 커서 정렬값이 createdAt 이 아니라 minPrice(12900) 여야 한다
        PageCursor decoded = PageTokens.decode(result.nextPageToken());
        assertThat(decoded.sortValue()).isEqualTo("12900");
    }
}