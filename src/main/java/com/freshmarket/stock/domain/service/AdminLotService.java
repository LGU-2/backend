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
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
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

    /*
     * 로트를 입고하고 INBOUND 변동 이력을 함께 남긴다.
     * 같은 requestId로 재시도가 오면(API-5-07, AIP-155) 새로 입고하지 않고 최초 결과를 그대로 돌려준다.
     * 먼저 조회해 일반적인(순차적인) 재시도를 검증 전에 걸러내고, save() 시점의 uk_lot_request_id
     * 위반은 두 요청이 거의 동시에 들어온 경합 상황을 잡는 안전망이다.
     */
    @Transactional
    public AdminLotResponse register(Long productId, Long optionId, AdminLotCreateRequest request) {
        Optional<StockLot> existingLot = stockLotRepository.findByRequestId(request.requestId());
        if (existingLot.isPresent()) {
            return AdminLotResponse.of(existingLot.get());
        }

        validateOptionExists(productId, optionId);
        LocalDate receivedDate = resolveReceivedDate(request.receivedDate());
        validateExpiryDate(receivedDate, request.expiryDate());

        StockLot stockLot = StockLot.register(request.requestId(), optionId, receivedDate, request.expiryDate(),
                request.initialQty());
        try {
            stockLotRepository.save(stockLot);
        } catch (DataIntegrityViolationException e) {
            if (isConstraintViolation(e, "uk_lot_request_id")) {
                return AdminLotResponse.of(findByRequestIdOrThrow(request.requestId()));
            }
            /*
             * validateOptionExists()로 이미 확인했지만, 그 직후 옵션이 삭제되는 경합이면 fk_lot_option
             * 위반이 날 수 있다. 지금은 옵션 삭제 기능이 없어 실제로 발생하진 않지만, AdminProductService의
             * 카테고리/공급처 처리와 같은 방식으로 방어해 둔다.
             */
            if (isConstraintViolation(e, "fk_lot_option")) {
                throw new StockException(StockErrorCode.OPTION_NOT_FOUND, e);
            }
            throw e;
        }

        StockMovement movement = StockMovement.inbound(stockLot.getId(), request.initialQty());
        stockMovementRepository.save(movement);

        return AdminLotResponse.of(stockLot);
    }

    // save() 시점에 uk_lot_request_id 위반이 났다면, 동시 재시도가 먼저 커밋을 마친 것이라 반드시 존재한다
    private StockLot findByRequestIdOrThrow(String requestId) {
        return stockLotRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException(
                        "request_id 유니크 위반 직후 재조회에 실패했다: " + requestId));
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

    // DB 예외의 근본 원인 메시지에 제약 이름이 들어있는지로 어떤 제약을 위반했는지 구분한다
    private boolean isConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
