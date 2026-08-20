package com.freshmarket.stock.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freshmarket.product.ProductApi;
import com.freshmarket.stock.domain.dto.AdminLotCreateRequest;
import com.freshmarket.stock.domain.dto.AdminLotResponse;
import com.freshmarket.stock.domain.entity.StockLot;
import com.freshmarket.stock.domain.exception.StockErrorCode;
import com.freshmarket.stock.domain.exception.StockException;
import com.freshmarket.stock.domain.repository.StockLotRepository;
import com.freshmarket.stock.domain.repository.StockMovementRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// AdminLotService의 등록 성공과 실패 케이스를 검증한다
@ExtendWith(MockitoExtension.class)
class AdminLotServiceTest {

    @Mock
    private StockLotRepository stockLotRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductApi productApi;

    @InjectMocks
    private AdminLotService adminLotService;

    @Test
    void 입고일을_지정하면_그대로_등록한다() {
        // given
        when(productApi.existsOption(12L, 31L)).thenReturn(true);
        stubSaveAssignsId();
        AdminLotCreateRequest request = new AdminLotCreateRequest(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 31), 200);

        // when
        AdminLotResponse result = adminLotService.register(12L, 31L, request);

        // then
        assertThat(result.stockLotId()).isEqualTo(77L);
        assertThat(result.productOptionId()).isEqualTo(31L);
        assertThat(result.receivedDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(result.expiryDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(result.initialQty()).isEqualTo(200);
        assertThat(result.availableQty()).isEqualTo(200);
        assertThat(result.status()).isEqualTo("AVAILABLE");
        verify(stockMovementRepository).save(any());
    }

    @Test
    void 입고일을_생략하면_오늘_날짜로_등록한다() {
        // given
        when(productApi.existsOption(12L, 31L)).thenReturn(true);
        stubSaveAssignsId();
        AdminLotCreateRequest request = new AdminLotCreateRequest(
                null, LocalDate.now().plusDays(10), 100);

        // when
        AdminLotResponse result = adminLotService.register(12L, 31L, request);

        // then
        assertThat(result.receivedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void 존재하지_않는_옵션으로_등록하면_실패한다() {
        // given
        when(productApi.existsOption(12L, 999L)).thenReturn(false);
        AdminLotCreateRequest request = new AdminLotCreateRequest(
                null, LocalDate.now().plusDays(10), 100);

        // when, then
        assertThatThrownBy(() -> adminLotService.register(12L, 999L, request))
                .isInstanceOf(StockException.class)
                .hasFieldOrPropertyWithValue("errorCode", StockErrorCode.OPTION_NOT_FOUND);
        verify(stockLotRepository, never()).save(any());
    }

    @Test
    void 소비기한이_입고일보다_이르면_실패한다() {
        // given
        when(productApi.existsOption(12L, 31L)).thenReturn(true);
        AdminLotCreateRequest request = new AdminLotCreateRequest(
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 10), 100);

        // when, then
        assertThatThrownBy(() -> adminLotService.register(12L, 31L, request))
                .isInstanceOf(StockException.class)
                .hasFieldOrPropertyWithValue("errorCode", StockErrorCode.EXPIRY_BEFORE_RECEIVED);
        verify(stockLotRepository, never()).save(any());
    }

    // 실제 저장이 없는 단위 테스트에서 JPA가 채워줄 생성 ID를 대신 채워준다.
    // StockMovement.inbound()가 stockLot.getId()를 그대로 넘겨받아 써야 해서 필요하다.
    private void stubSaveAssignsId() {
        when(stockLotRepository.save(any())).thenAnswer(invocation -> {
            StockLot stockLot = invocation.getArgument(0);
            ReflectionTestUtils.setField(stockLot, "id", 77L);
            return stockLot;
        });
    }
}
