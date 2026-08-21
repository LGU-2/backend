package com.freshmarket.order.domain.service;

import com.freshmarket.order.domain.dto.OrderDetailResponse;
import com.freshmarket.order.domain.dto.OrderItemResponse;
import com.freshmarket.order.domain.dto.OrderSearchCondition;
import com.freshmarket.order.domain.entity.Order;
import com.freshmarket.order.domain.exception.OrderErrorCode;
import com.freshmarket.order.domain.exception.OrderException;
import com.freshmarket.order.domain.repository.OrderItemRepository;
import com.freshmarket.order.domain.repository.OrderQueryRepository;
import com.freshmarket.order.domain.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderItemRepository orderItemRepository;

    public Page<Order> getOrders(Long memberId, OrderSearchCondition condition, Pageable pageable) {
        validatePeriod(condition);
        return orderQueryRepository.findAllByMemberIdAndCondition(memberId, condition, pageable);
    }

    public OrderDetailResponse getOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
        List<OrderItemResponse> items = orderItemRepository.findAllByOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(OrderItemResponse::from)
                .toList();
        return OrderDetailResponse.from(order, items);
    }

    private void validatePeriod(OrderSearchCondition condition) {
        if (condition.from() != null && condition.to() != null && condition.from().isAfter(condition.to())) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_PERIOD);
        }
    }
}
