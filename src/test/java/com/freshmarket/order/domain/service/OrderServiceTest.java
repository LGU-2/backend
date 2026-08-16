package com.freshmarket.order.domain.service;

import com.freshmarket.order.domain.entity.Order;
import com.freshmarket.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void 주문을_조회한다() {
        when(orderRepository.findById(any())).thenReturn(Optional.of(new Order()));
        assertThat(orderService.findOrder(1L)).isNotNull();
    }

    @Test
    void 회원의_주문을_모두_조회한다() {
        when(orderRepository.findByMemberId(any())).thenReturn(List.of(new Order()));
        assertThat(orderService.findAll(1L)).hasSize(1);
    }

    @Test
    void 주문을_취소한다() {
        when(orderRepository.findById(any())).thenReturn(Optional.of(new Order()));
        orderService.cancel(1L);
        verify(orderRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
