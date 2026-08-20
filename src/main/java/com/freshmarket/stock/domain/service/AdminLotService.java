package com.freshmarket.stock.domain.service;

import com.freshmarket.product.ProductApi;
import com.freshmarket.stock.domain.dto.AdminLotCreateRequest;
import com.freshmarket.stock.domain.dto.AdminLotResponse;
import com.freshmarket.stock.domain.entity.StockLot;
import com.freshmarket.stock.domain.entity.StockMovement;
import com.freshmarket.stock.domain.exception.StockErrorCode;
import com.freshmarket.stock.domain.exception.StockException;
import com.freshmarket.stock.domain.repository.StockLotRepository;
import com.freshmarket.stock.domain.repository.StockMovementRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 화면에서 로트를 입고 등록하는 기능을 담당한다
@Service
@Transactional(readOnly = true)
public class AdminLotService {

    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductApi productApi;

    public AdminLotService(StockLotRepository stockLotRepository,
            StockMovementRepository stockMovementRepository, ProductApi productApi) {
        this.stockLotRepository = stockLotRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productApi = productApi;
    }

    // 로트를 입고하고 INBOUND 변동 이력을 함께 남긴다
    @Transactional
    public AdminLotResponse register(Long productId, Long optionId, AdminLotCreateRequest request) {
        validateOptionExists(productId, optionId);
        LocalDate receivedDate = resolveReceivedDate(request.receivedDate());
        validateExpiryDate(receivedDate, request.expiryDate());

        StockLot stockLot = StockLot.register(optionId, receivedDate, request.expiryDate(), request.initialQty());
        stockLotRepository.save(stockLot);

        StockMovement movement = StockMovement.inbound(stockLot.getId(), request.initialQty());
        stockMovementRepository.save(movement);

        return AdminLotResponse.of(stockLot);
    }

    // optionId가 productId 소속으로 실제 존재하는지 확인한다
    private void validateOptionExists(Long productId, Long optionId) {
        if (!productApi.existsOption(productId, optionId)) {
            throw new StockException(StockErrorCode.OPTION_NOT_FOUND);
        }
    }

    // 생략된 입고일은 오늘로 채운다 (stock.md: "기본 오늘")
    private LocalDate resolveReceivedDate(LocalDate receivedDate) {
        return receivedDate != null ? receivedDate : LocalDate.now();
    }

    // 소비기한이 입고일보다 이르면 STOCK-001
    private void validateExpiryDate(LocalDate receivedDate, LocalDate expiryDate) {
        if (expiryDate.isBefore(receivedDate)) {
            throw new StockException(StockErrorCode.EXPIRY_BEFORE_RECEIVED);
        }
    }
}
