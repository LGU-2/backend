package com.freshmarket.order.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    private Long memberId;

    private String status;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items = new ArrayList<>();
}
