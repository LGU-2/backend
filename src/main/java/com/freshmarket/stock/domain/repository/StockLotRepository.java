package com.freshmarket.stock.domain.repository;

import com.freshmarket.stock.domain.entity.StockLot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 로트(StockLot) 기본 CRUD
public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    // 요청 식별자로 이미 등록된 로트를 찾는다. 재시도 감지에 쓰인다
    Optional<StockLot> findByRequestId(String requestId);
}
