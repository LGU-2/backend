package com.freshmarket.stock.domain.repository;

import com.freshmarket.stock.domain.entity.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;

// 로트(StockLot) 기본 CRUD
public interface StockLotRepository extends JpaRepository<StockLot, Long> {
}
