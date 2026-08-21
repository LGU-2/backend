package com.freshmarket.product.domain.exception;

import com.freshmarket.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// product 도메인 전체(카테고리, 상품 등)가 함께 쓰는 오류 코드 모음
@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT-001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_DUPLICATE_NAME(HttpStatus.CONFLICT, "PRODUCT-002", "이미 같은 이름의 카테고리가 있습니다."),
    CATEGORY_HAS_PRODUCTS(HttpStatus.CONFLICT, "PRODUCT-003", "소속된 상품이 있어 삭제할 수 없습니다."),
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "PRODUCT-004", "하위 카테고리가 있어 삭제할 수 없습니다."),
    /*
     * 명세는 상품 미존재에 PRODUCT-001 을 배정했으나 그 값이 카테고리 오류에 이미 쓰이고 있다.
     * PRODUCT-005~007 은 재웅님이 상품 등록(#13)에서 쓰기로 예약해 008 을 쓴다.
     * 카테고리 오류가 CATEGORY- 로 리네임되면 명세대로 001 로 되돌릴 수 있다.
     */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT-008",
            "상품을 찾을 수 없습니다. 상품 목록에서 다시 확인해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}