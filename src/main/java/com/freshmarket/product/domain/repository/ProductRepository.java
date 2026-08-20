package com.freshmarket.product.domain.repository;

import com.freshmarket.product.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

// 기본 CRUD. 동적 조회는 ProductQueryRepository 가 맡는다
public interface ProductRepository extends JpaRepository<Product, Long> {
}