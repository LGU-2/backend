package com.freshmarket.stock.domain.repository;

import com.freshmarket.stock.domain.entity.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {
}
