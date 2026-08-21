package com.freshmarket.stock.domain.exception;

import com.freshmarket.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// stock 도메인이 쓰는 오류 코드 모음
@Getter
@RequiredArgsConstructor
public enum StockErrorCode implements ErrorCode {

    EXPIRY_BEFORE_RECEIVED(HttpStatus.UNPROCESSABLE_CONTENT, "STOCK-001", "소비기한이 입고일보다 이릅니다."),
    // 메시지는 "삭제된 상품"까지 말하지만, 실제로는 존재 여부만 본다(상품 삭제 기능이 아직 없어 deleted_at이 채워질 방법이 없다).
    // 상품 삭제 기능이 생기면 그때 이 판정에도 삭제 여부 확인을 추가해야 한다.
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK-002", "없거나 삭제된 상품입니다."),
    // STOCK-003~005는 재고 조정·폐기 이슈에서 쓰기로 stock.md에 이미 예약돼 있어 건너뛴다
    REGISTRATION_IN_PROGRESS(HttpStatus.CONFLICT, "STOCK-006", "동일한 요청이 아직 처리 중입니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
