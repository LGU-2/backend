package com.freshmarket.common.exception;

import org.springframework.http.HttpStatus;

// 공통 코드는 CommonErrorCode이며 도메인마다 자기 ErrorCode enum 을 두고 해당 인터페이스를 구현
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}
