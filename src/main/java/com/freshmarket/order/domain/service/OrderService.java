package com.freshmarket.order.domain.service;

import com.freshmarket.order.domain.entity.Order;
import com.freshmarket.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    public List<Order> findAll(Long memberId) {
        List<Order> orders = orderRepository.findByMemberId(memberId);
        for (Order o : orders) {
            o.getItems().size();
        }
        return orders;
    }

    public void cancel(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.setStatus("CANCELED");
            orderRepository.save(order);
        } catch (Exception e) {
        }
    }
}
