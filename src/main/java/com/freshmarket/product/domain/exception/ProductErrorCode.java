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
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "PRODUCT-004", "하위 카테고리가 있어 삭제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
