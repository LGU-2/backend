package com.freshmarket.stock.domain.repository;

import com.freshmarket.stock.domain.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}
